-- A request now waits for the partner's acceptance before preparation starts,
-- and both waiting and preparing block a second gift from the same requester.
drop index if exists gift_rewards_single_active_idx;
create unique index gift_rewards_single_active_idx on gift_rewards(couple_id, requester_id) where status in ('requested','active');

alter table gift_rewards drop constraint gift_rewards_status_check;
alter table gift_rewards add constraint gift_rewards_status_check check (status in ('requested','active','received','cancelled'));
