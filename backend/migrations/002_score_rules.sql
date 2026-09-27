alter table invites
  add column if not exists initial_score integer not null default 0,
  add column if not exists min_score integer,
  add column if not exists max_score integer,
  add column if not exists add_min integer not null default 1,
  add column if not exists add_max integer not null default 5,
  add column if not exists subtract_min integer not null default 1,
  add column if not exists subtract_max integer not null default 5;

alter table score_settings
  add column if not exists add_min integer not null default 1,
  add column if not exists add_max integer not null default 5,
  add column if not exists subtract_min integer not null default 1,
  add column if not exists subtract_max integer not null default 5;

alter table invites
  drop constraint if exists invites_score_rules_check;
alter table invites
  add constraint invites_score_rules_check check (
    (min_score is null or max_score is null or min_score <= max_score)
    and add_min between 1 and 100 and add_max between add_min and 100
    and subtract_min between 1 and 100 and subtract_max between subtract_min and 100
  );

alter table score_settings
  drop constraint if exists score_settings_score_rules_check;
alter table score_settings
  add constraint score_settings_score_rules_check check (
    add_min between 1 and 100 and add_max between add_min and 100
    and subtract_min between 1 and 100 and subtract_max between subtract_min and 100
  );
