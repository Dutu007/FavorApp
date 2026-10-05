-- Goals become per-user: each partner sets their own threshold.
drop table if exists gift_goals;
create table gift_goals (
  couple_id uuid not null references couples(id) on delete cascade,
  user_id uuid not null references app_users(id) on delete cascade,
  target_score integer not null check (target_score >= 1),
  updated_by uuid not null references app_users(id) on delete cascade,
  updated_at timestamptz not null default now(),
  primary key (couple_id, user_id)
);

-- The fixed 4-step pipeline becomes a custom timeline: steps is an ordered
-- jsonb array of {label, at} nodes and current_step counts completed nodes.
alter table gift_rewards
  add column steps jsonb not null default '[]'::jsonb,
  add column current_step integer not null default 0;

-- Carry existing gifts over: old fixed statuses map onto the default nodes.
update gift_rewards set
  current_step = case status
    when 'confirmed' then 1 when 'preparing' then 2 when 'shipped' then 3 when 'received' then 4 else 0 end,
  steps = jsonb_build_array(
    jsonb_build_object('label', '已确认礼物', 'at', confirmed_at),
    jsonb_build_object('label', '已采购材料/已下单礼物', 'at', preparing_at),
    jsonb_build_object('label', '已发货', 'at', shipped_at),
    jsonb_build_object('label', '已收到礼物', 'at', received_at)
  );

alter table gift_rewards alter column status set default 'active';
alter table gift_rewards drop constraint gift_rewards_status_check;
alter table gift_rewards add constraint gift_rewards_status_check check (status in ('active','received','cancelled'));
update gift_rewards set status = 'active' where status in ('requested','confirmed','preparing','shipped');

alter table gift_rewards
  drop column confirmed_at,
  drop column preparing_at,
  drop column shipped_at,
  drop column received_at;

drop index if exists gift_rewards_single_active_idx;
-- Each partner can keep one gift of their own in flight at the same time.
create unique index gift_rewards_single_active_idx on gift_rewards(couple_id, requester_id) where status = 'active';
