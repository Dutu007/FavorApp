create table if not exists gift_goals (
  couple_id uuid primary key references couples(id) on delete cascade,
  target_score integer not null check (target_score >= 1),
  updated_by uuid not null references app_users(id) on delete cascade,
  updated_at timestamptz not null default now()
);

create table if not exists gift_rewards (
  id uuid primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  requester_id uuid not null references app_users(id) on delete cascade,
  title text not null,
  kind text not null check (kind in ('handmade','ready')),
  note text,
  status text not null default 'requested' check (status in ('requested','confirmed','preparing','shipped','received','cancelled')),
  confirmed_at timestamptz,
  preparing_at timestamptz,
  shipped_at timestamptz,
  received_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists gift_rewards_couple_idx on gift_rewards(couple_id, created_at desc);
-- One gift in flight per couple: redeem, walk the timeline, then the next stage.
create unique index if not exists gift_rewards_single_active_idx on gift_rewards(couple_id) where status in ('requested','confirmed','preparing','shipped');
