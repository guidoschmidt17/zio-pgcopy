begin;

-- increase write performance

alter database simple set synchronous_commit to off;

-- tables, indexes

create unlogged table simple (
  i int4 not null
  ) with (autovacuum_enabled = off);

-- eof

commit;
