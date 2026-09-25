-- Keep the configured initial score in sync with balances until the first event.
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
  configured_initial_score integer;
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
  values (couple_id)
  returning initial_score into configured_initial_score;

  insert into public.couple_scores (couple_id, target_user_id, current_score)
  values (couple_id, invite_row.inviter_id, configured_initial_score),
         (couple_id, current_user_id, configured_initial_score);

  update public.couple_invites
  set used_at = now(), used_by = current_user_id
  where id = invite_row.id;

  return couple_id;
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

  select exists (
    select 1 from public.score_events where couple_id = target_couple_id
  ) into has_events;

  if not has_events and exists (
    select 1 from public.couple_scores
    where couple_id = target_couple_id
      and (new_initial_score < coalesce(new_min_score, new_initial_score)
        or new_initial_score > coalesce(new_max_score, new_initial_score))
  ) then
    raise exception 'initial_score_outside_range';
  end if;

  if has_events and exists (
    select 1 from public.couple_scores
    where couple_id = target_couple_id
      and (new_min_score is not null and current_score < new_min_score
        or new_max_score is not null and current_score > new_max_score)
  ) then
    raise exception 'range_does_not_include_current_score';
  end if;

  update public.score_settings
  set initial_score = new_initial_score,
      min_score = new_min_score,
      max_score = new_max_score,
      updated_at = now()
  where couple_id = target_couple_id
  returning * into result_row;

  if not has_events then
    update public.couple_scores
    set current_score = new_initial_score, updated_at = now()
    where couple_id = target_couple_id;
  end if;

  return result_row;
end;
$$;

revoke all on function public.accept_invite(text) from public, anon;
revoke all on function public.update_score_settings(uuid, integer, integer, integer) from public, anon;
grant execute on function public.accept_invite(text) to authenticated;
grant execute on function public.update_score_settings(uuid, integer, integer, integer) to authenticated;
