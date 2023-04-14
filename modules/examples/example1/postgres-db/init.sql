begin;

-- increase write performance

alter database world set synchronous_commit to off;

-- types

create type eventcategory as enum('Created', 'Read', 'Updated', 'Deleted', 'Meta');

-- tables, indexes

create unlogged table fact (
  serialid bigserial primary key, 
  created timestamptz not null default now(),
  aggregateid uuid not null,
  aggregatelatest int4 not null,
  eventcategory eventcategory not null,
  eventid uuid not null,
  eventdatalength int4 not null,
  eventdata bytea not null,
  tags text[] not null,
  big numeric not null, 
  jb jsonb not null,
  j json not null
  ) with (autovacuum_enabled = off);

-- eof

commit;
