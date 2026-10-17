alter table couples
  add column if not exists together_since date;

alter table couples
  add constraint couples_together_since_range_check check (
    together_since is null
    or together_since between date '0001-01-01' and date '9999-12-31'
  );
