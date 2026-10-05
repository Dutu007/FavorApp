-- The gift kind (handmade vs ready-made) is gone: the wish text and the
-- custom progress nodes carry everything the couple needs.
alter table gift_rewards drop column kind;
