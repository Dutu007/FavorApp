create table if not exists app_users (
  id uuid primary key,
  username text not null unique,
  display_name text not null,
  password_hash text not null,
  created_at timestamptz not null default now()
);

create table if not exists sessions (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  token_hash bytea not null unique,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists sessions_token_hash_idx on sessions(token_hash);

create table if not exists couples (
  id uuid primary key,
  member_a uuid not null references app_users(id) on delete cascade,
  member_b uuid not null references app_users(id) on delete cascade,
  status text not null default 'active' check (status = 'active'),
  created_at timestamptz not null default now(),
  check (member_a <> member_b),
  unique (member_a, member_b)
);
create unique index if not exists couples_pair_idx on couples(least(member_a, member_b), greatest(member_a, member_b));

create table if not exists invites (
  id uuid primary key,
  inviter_id uuid not null references app_users(id) on delete cascade,
  code_hash bytea not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  used_by uuid references app_users(id),
  created_at timestamptz not null default now()
);

create table if not exists score_settings (
  couple_id uuid primary key references couples(id) on delete cascade,
  initial_score integer not null default 0,
  min_score integer,
  max_score integer,
  updated_at timestamptz not null default now(),
  check (min_score is null or max_score is null or min_score <= max_score)
);

create table if not exists couple_scores (
  couple_id uuid not null references couples(id) on delete cascade,
  target_user_id uuid not null references app_users(id) on delete cascade,
  current_score integer not null,
  updated_at timestamptz not null default now(),
  primary key (couple_id, target_user_id)
);

create table if not exists score_events (
  id uuid primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  actor_id uuid not null references app_users(id) on delete cascade,
  target_user_id uuid not null references app_users(id) on delete cascade,
  idempotency_key text not null,
  delta integer not null check (delta <> 0),
  score_after integer not null,
  note text,
  created_at timestamptz not null default now(),
  unique (actor_id, idempotency_key)
);
create index if not exists score_events_feed_idx on score_events(couple_id, created_at desc);
