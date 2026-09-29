create unique index if not exists rule_change_requests_one_pending_idx
  on rule_change_requests(couple_id) where status = 'pending';
