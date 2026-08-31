-- ---------------------------------------------------------------------------
-- Picks belong to a group.
--
-- V13 built the groups domain but left picking global. This migration attaches
-- every existing pick to the first group and makes the group part of a pick's
-- identity, so the same member can play the same game in two leagues.
--
-- Unlike V13 this rewrites existing rows. Nothing is dropped: the column is
-- added empty, backfilled, and only then made NOT NULL, so a failure at any
-- step leaves the data intact rather than half-converted.
--
-- The backfill target is the group created through the admin screen on
-- 2026-08-30. Hard-coded rather than "the oldest group" so the statement means
-- the same thing on every database it runs against - on a fresh test database
-- there are no picks and every statement here is a no-op.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- pick
-- ---------------------------------------------------------------------------
alter table public.pick add column group_id uuid;

update public.pick
   set group_id = '57152bbf-b6b1-449e-8feb-21ab3ff51123'
 where group_id is null;

-- Fails loudly if a row could not be attributed, rather than leaving a pick
-- that belongs to no league.
alter table public.pick alter column group_id set not null;

-- Deleting a group destroys its picks - the delete confirmation says so.
alter table public.pick
    add constraint pick_group_fkey
    foreign key (group_id) references public.pick_group (id) on delete cascade;

-- One pick per market per game *per group*. Without the group in the key a
-- member could not play the same game in two leagues.
alter table public.pick drop constraint if exists pick_user_game_market_key;
alter table public.pick add constraint pick_group_user_game_market_key
    unique (group_id, user_id, game_id, market);

-- The leaderboard reads every pick in one group for one season.
create index pick_group_user_idx on public.pick (group_id, user_id);

-- ---------------------------------------------------------------------------
-- weekly_entry -> cadence_entry
--
-- The counter row that makes the pick cap atomic. Its identity *is* the
-- counting period, and "week" was hard-coded into that identity - which a
-- group on a daily cadence cannot use. The replacement keys on an opaque
-- period label that the application derives from the group's cadence
-- (CadencePeriod), so weekly and daily groups share one mechanism.
--
-- The 0..10 check is gone with it: the cap is now per-group and may be absent
-- entirely, so the upper bound cannot live in the schema. Only the floor
-- survives, which is all the constraint was ever really protecting.
-- ---------------------------------------------------------------------------
create table public.cadence_entry (
    group_id   uuid        not null references public.pick_group (id) on delete cascade,
    user_id    uuid        not null references public.app_user (id)   on delete cascade,
    -- '2026-W01' for a weekly group, '2026-09-05' for a daily one. Opaque to
    -- the database on purpose - the format is the application's business.
    period_key varchar(16) not null,
    pick_count integer     not null default 0 check (pick_count >= 0),
    updated_at timestamptz not null default now(),
    primary key (group_id, user_id, period_key)
);

insert into public.cadence_entry (group_id, user_id, period_key, pick_count, updated_at)
select '57152bbf-b6b1-449e-8feb-21ab3ff51123',
       user_id,
       season || '-W' || lpad(week::text, 2, '0'),
       pick_count,
       updated_at
  from public.weekly_entry;

drop table public.weekly_entry;

alter table public.cadence_entry enable row level security;
