-- ---------------------------------------------------------------------------
-- Closing out a period.
--
-- Slice 8 gave groups per-market maximums, which a pick can be judged against
-- the moment it is made. Minimums cannot work that way: a member who has made
-- no picks yet has not broken a minimum, they are simply early. The rule only
-- becomes decidable when the period stops accepting picks - once the last game
-- in it has kicked off.
--
-- So a minimum is settled rather than enforced, and settling needs two things
-- the pick table cannot express:
--
--   cadence_settlement  which periods have been closed out, so the job is
--                       idempotent and does not re-charge on every run
--   cadence_penalty     the shortfalls it charged, as losses
--
-- A penalty is a loss that has no pick behind it, which is exactly why it
-- cannot live in `pick`: that table's rows each point at a game, carry a locked
-- line and are graded from a score. A synthetic row with none of those would
-- have to be excluded from half the queries that read it.
-- ---------------------------------------------------------------------------

create table public.cadence_settlement (
    group_id   uuid        not null references public.pick_group (id) on delete cascade,
    -- Same key CadencePeriod builds: '2026-W03' weekly, '2026-09-05' daily.
    period_key varchar(16) not null,
    settled_at timestamptz not null default now(),
    primary key (group_id, period_key)
);

create table public.cadence_penalty (
    id         uuid         primary key default gen_random_uuid(),
    group_id   uuid         not null references public.pick_group (id) on delete cascade,
    user_id    uuid         not null references public.app_user (id)   on delete cascade,
    period_key varchar(16)  not null,

    -- Which minimum was missed. Null means the group's overall minimum picks
    -- per period, which names no market of its own.
    market     varchar(8)   check (market is null or market in ('SPREAD','TOTAL','WINNER')),

    -- How many picks short they finished. Always at least one: a row is only
    -- written when there is something to charge.
    shortfall  integer      not null check (shortfall > 0),

    -- What the shortfall cost, worked out from the group's scoring at
    -- settlement time and stored rather than re-derived. A group that changes
    -- its point values later must not silently rewrite the standings of
    -- periods that already closed under the old ones.
    points     numeric(8,2) not null,

    created_at timestamptz  not null default now(),

    -- One row per member per period per minimum, so a re-run updates rather
    -- than accumulating a second charge for the same failure.
    constraint cadence_penalty_unique unique nulls not distinct (group_id, user_id, period_key, market)
);

create index cadence_penalty_group_user_idx on public.cadence_penalty (group_id, user_id);

alter table public.cadence_settlement enable row level security;
alter table public.cadence_penalty    enable row level security;
