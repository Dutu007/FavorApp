alter table app_users
  add column if not exists avatar bytea,
  add column if not exists avatar_updated_at timestamptz;
