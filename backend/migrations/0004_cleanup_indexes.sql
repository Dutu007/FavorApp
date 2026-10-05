create index if not exists sessions_expires_idx on sessions(expires_at);
create index if not exists invites_expires_idx on invites(expires_at);
