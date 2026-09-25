create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  avatar_url text,
  created_at timestamptz not null default now()
);

create table public.couples (
  id uuid primary key default gen_random_uuid(),
  member_a uuid not null references public.profiles(id) on delete cascade,
  member_b uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'active' check (status in ('active', 'ended')),
  created_at timestamptz not null default now(),
  check (member_a <> member_b)
);

create unique index couples_unique_pair
  on public.couples (least(member_a, member_b), greatest(member_a, member_b));

create table public.couple_invites (
  id uuid primary key default gen_random_uuid(),
  inviter_id uuid not null references public.profiles(id) on delete cascade,
  code_hash bytea not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  used_by uuid references public.profiles(id),
  created_at timestamptz not null default now()
);

create table public.score_settings (
  couple_id uuid primary key references public.couples(id) on delete cascade,
  initial_score integer not null default 0,
  min_score integer,
  max_score integer,
  updated_at timestamptz not null default now(),
  check (min_score is null or max_score is null or min_score <= max_score)
);

create table public.couple_scores (
  couple_id uuid not null references public.couples(id) on delete cascade,
  target_user_id uuid not null references public.profiles(id) on delete cascade,
  current_score integer not null,
  updated_at timestamptz not null default now(),
  primary key (couple_id, target_user_id)
);

create table public.score_events (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.couples(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade,
  target_user_id uuid not null references public.profiles(id) on delete cascade,
  delta integer not null check (delta <> 0),
  score_after integer not null,
  note text,
  created_at timestamptz not null default now(),
  check (actor_id <> target_user_id)
);

create index score_events_couple_created_at
  on public.score_events (couple_id, created_at desc);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', ''));
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create or replace function public.create_invite()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  invite_code text;
begin
  if current_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if exists (
    select 1 from public.couples
    where status = 'active'
      and (member_a = current_user_id or member_b = current_user_id)
  ) then
    raise exception 'already_matched';
  end if;

  invite_code := upper(substr(encode(extensions.gen_random_bytes(8), 'hex'), 1, 8));
  insert into public.couple_invites (inviter_id, code_hash, expires_at)
  values (current_user_id, extensions.digest(invite_code, 'sha256'), now() + interval '24 hours');
  return invite_code;
end;
$$;

create or replace function public.accept_invite(input_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  invite_row public.couple_invites%rowtype;
  couple_id uuid;
begin
  if current_user_id is null then
    raise exception 'not_authenticated';
  end if;
  if length(trim(input_code)) = 0 then
    raise exception 'invalid_code';
  end if;
  if exists (
    select 1 from public.couples
    where status = 'active'
      and (member_a = current_user_id or member_b = current_user_id)
  ) then
    raise exception 'already_matched';
  end if;

  select * into invite_row
  from public.couple_invites
  where code_hash = extensions.digest(upper(trim(input_code)), 'sha256')
  for update;

  if not found or invite_row.used_at is not null or invite_row.expires_at <= now() then
    raise exception 'invalid_or_expired_code';
  end if;
  if invite_row.inviter_id = current_user_id then
    raise exception 'cannot_match_self';
  end if;

  insert into public.couples (member_a, member_b)
  values (invite_row.inviter_id, current_user_id)
  returning id into couple_id;

  insert into public.score_settings (couple_id)
  values (couple_id);
  insert into public.couple_scores (couple_id, target_user_id, current_score)
  values (couple_id, invite_row.inviter_id, 0), (couple_id, current_user_id, 0);

  update public.couple_invites
  set used_at = now(), used_by = current_user_id
  where id = invite_row.id;

  return couple_id;
end;
$$;

create or replace function public.add_score_event(
  target_couple_id uuid,
  target_user_id uuid,
  score_delta integer,
  event_note text default null
)
returns public.score_events
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  balance_row public.couple_scores%rowtype;
  settings_row public.score_settings%rowtype;
  result_row public.score_events%rowtype;
  next_score integer;
begin
  if current_user_id is null then
    raise exception 'not_authenticated';
  end if;
  if score_delta = 0 then
    raise exception 'delta_must_not_be_zero';
  end if;
  if not exists (
    select 1 from public.couples
    where id = target_couple_id and status = 'active'
      and (member_a = current_user_id or member_b = current_user_id)
  ) then
    raise exception 'not_a_couple_member';
  end if;
  if not exists (
    select 1 from public.couples c
    where c.id = target_couple_id
      and add_score_event.target_user_id in (c.member_a, c.member_b)
      and add_score_event.target_user_id <> current_user_id
  ) then
    raise exception 'invalid_target';
  end if;

  select cs.* into balance_row
  from public.couple_scores cs
  where cs.couple_id = target_couple_id and cs.target_user_id = add_score_event.target_user_id
  for update;
  select * into settings_row from public.score_settings where couple_id = target_couple_id;

  next_score := balance_row.current_score + score_delta;
  if settings_row.min_score is not null and next_score < settings_row.min_score then
    raise exception 'below_minimum';
  end if;
  if settings_row.max_score is not null and next_score > settings_row.max_score then
    raise exception 'above_maximum';
  end if;

  update public.couple_scores cs
  set current_score = next_score, updated_at = now()
  where cs.couple_id = target_couple_id and cs.target_user_id = add_score_event.target_user_id;

  insert into public.score_events (couple_id, actor_id, target_user_id, delta, score_after, note)
  values (target_couple_id, current_user_id, add_score_event.target_user_id, score_delta, next_score, nullif(trim(event_note), ''))
  returning * into result_row;
  return result_row;
end;
$$;

create or replace function public.update_score_settings(
  target_couple_id uuid,
  new_initial_score integer,
  new_min_score integer default null,
  new_max_score integer default null
)
returns public.score_settings
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid := auth.uid();
  result_row public.score_settings%rowtype;
  has_events boolean;
begin
  if current_user_id is null then
    raise exception 'not_authenticated';
  end if;
  if not exists (
    select 1 from public.couples c
    where c.id = target_couple_id
      and c.status = 'active'
      and (c.member_a = current_user_id or c.member_b = current_user_id)
  ) then
    raise exception 'not_a_couple_member';
  end if;
  if new_min_score is not null and new_max_score is not null and new_min_score > new_max_score then
    raise exception 'invalid_score_range';
  end if;

  perform 1 from public.score_settings where couple_id = target_couple_id for update;
  select exists (
    select 1 from public.score_events where couple_id = target_couple_id
  ) into has_events;

  if not has_events then
    if new_min_score is not null and new_initial_score < new_min_score then
      raise exception 'initial_below_minimum';
    end if;
    if new_max_score is not null and new_initial_score > new_max_score then
      raise exception 'initial_above_maximum';
    end if;
    update public.couple_scores
    set current_score = new_initial_score, updated_at = now()
    where couple_id = target_couple_id;
  else
    if exists (
      select 1 from public.couple_scores
      where couple_id = target_couple_id
        and (new_min_score is not null and current_score < new_min_score
          or new_max_score is not null and current_score > new_max_score)
    ) then
      raise exception 'range_does_not_include_current_score';
    end if;
  end if;

  update public.score_settings
  set initial_score = new_initial_score,
      min_score = new_min_score,
      max_score = new_max_score,
      updated_at = now()
  where couple_id = target_couple_id
  returning * into result_row;
  return result_row;
end;
$$;

alter table public.profiles enable row level security;
alter table public.couples enable row level security;
alter table public.couple_invites enable row level security;
alter table public.score_settings enable row level security;
alter table public.couple_scores enable row level security;
alter table public.score_events enable row level security;

revoke all on table public.profiles, public.couples, public.couple_invites,
  public.score_settings, public.couple_scores, public.score_events from anon;
grant select on table public.profiles, public.couples, public.score_settings,
  public.couple_scores, public.score_events to authenticated;
grant update on table public.profiles to authenticated;

create policy profiles_select on public.profiles for select to authenticated
using (
  id = auth.uid() or exists (
    select 1 from public.couples c
    where c.status = 'active'
      and (c.member_a = auth.uid() or c.member_b = auth.uid())
      and (c.member_a = profiles.id or c.member_b = profiles.id)
  )
);
create policy profiles_update_own on public.profiles for update to authenticated
using (id = auth.uid()) with check (id = auth.uid());

create policy couples_select_members on public.couples for select to authenticated
using (member_a = auth.uid() or member_b = auth.uid());

create policy settings_select_members on public.score_settings for select to authenticated
using (exists (select 1 from public.couples c where c.id = couple_id and (c.member_a = auth.uid() or c.member_b = auth.uid())));
create policy scores_select_members on public.couple_scores for select to authenticated
using (exists (select 1 from public.couples c where c.id = couple_id and (c.member_a = auth.uid() or c.member_b = auth.uid())));

create policy events_select_members on public.score_events for select to authenticated
using (exists (select 1 from public.couples c where c.id = couple_id and (c.member_a = auth.uid() or c.member_b = auth.uid())));

revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.create_invite() from public, anon;
revoke all on function public.accept_invite(text) from public, anon;
revoke all on function public.add_score_event(uuid, uuid, integer, text) from public, anon;
revoke all on function public.update_score_settings(uuid, integer, integer, integer) from public, anon;
grant execute on function public.create_invite() to authenticated;
grant execute on function public.accept_invite(text) to authenticated;
grant execute on function public.add_score_event(uuid, uuid, integer, text) to authenticated;
grant execute on function public.update_score_settings(uuid, integer, integer, integer) to authenticated;

alter publication supabase_realtime add table public.couples, public.score_settings, public.couple_scores, public.score_events;
