create table if not exists rule_change_requests (
  id uuid primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  requester_id uuid not null references app_users(id) on delete cascade,
  initial_score integer not null default 0,
  min_score integer,
  max_score integer,
  add_min integer not null default 1,
  add_max integer not null default 5,
  subtract_min integer not null default 1,
  subtract_max integer not null default 5,
  status text not null default 'pending' check (status in ('pending','accepted','rejected','cancelled')),
  created_at timestamptz not null default now(),
  responded_at timestamptz
);
create index if not exists rule_change_requests_couple_idx on rule_change_requests(couple_id, status, created_at desc);
