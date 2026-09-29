-- Collapse any duplicate pending requests (from before the concurrency guard)
-- so the partial unique index below cannot fail on existing data.
delete from rule_change_requests a
  using rule_change_requests b
  where a.couple_id = b.couple_id
    and a.status = 'pending'
    and b.status = 'pending'
    and a.id <> b.id
    and (a.created_at < b.created_at or (a.created_at = b.created_at and a.id < b.id));

create unique index if not exists rule_change_requests_one_pending_idx
  on rule_change_requests(couple_id) where status = 'pending';
