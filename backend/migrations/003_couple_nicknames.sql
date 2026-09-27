alter table couples
  add column if not exists member_a_nickname text not null default '',
  add column if not exists member_b_nickname text not null default '';

alter table couples
  drop constraint if exists couples_nickname_length_check;
alter table couples
  add constraint couples_nickname_length_check check (
    char_length(member_a_nickname) <= 8 and char_length(member_b_nickname) <= 8
  );

