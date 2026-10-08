create table agreements (
  id uuid primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  creator_id uuid not null references app_users(id) on delete cascade,
  idempotency_key text not null check (char_length(idempotency_key) between 1 and 128),
  title text not null check (char_length(title) between 1 and 40 and btrim(title) <> ''),
  note text not null default '' check (char_length(note) <= 2000),
  due_date date,
  completed boolean not null default false,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version integer not null default 1 check (version >= 1),
  -- Keep the key after deletion so retries cannot recreate a deleted agreement.
  deleted_at timestamptz,
  unique (couple_id, creator_id, idempotency_key),
  check (completed = (completed_at is not null))
);

create index agreements_creator_idx on agreements(creator_id);
create index agreements_pending_feed_idx
  on agreements(couple_id, due_date asc nulls last, created_at desc, id)
  where not completed and deleted_at is null;
create index agreements_completed_feed_idx
  on agreements(couple_id, completed_at desc, id)
  where completed and deleted_at is null;
