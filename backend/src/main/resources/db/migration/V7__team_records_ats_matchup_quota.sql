-- ---------------------------------------------------------------------------
-- Four small additions:
--   1. team_record   - season win/loss splits, admin-triggered like teams/coaches
--   2. team_ats      - against-the-spread record, refreshed on demand
--   3. team_matchup  - all-time head-to-head history between two programs
--   4. cfbd_quota_snapshot - the real /info numbers, throttled to once a day
--
-- team_id columns are deliberately unconstrained, same reasoning as V2: not
-- every team CFBD reports on (lower divisions, historical opponents in a
-- matchup's game list) has a row in public.team.
-- ---------------------------------------------------------------------------

create table public.team_record (
    id                  bigserial primary key,
    team_id             integer      not null,
    season              integer      not null,
    classification      varchar(16),
    conference          varchar(64),
    division            varchar(64),
    expected_wins       numeric(5,2),
    total_games         integer,
    total_wins          integer,
    total_losses        integer,
    total_ties          integer,
    conference_games    integer,
    conference_wins     integer,
    conference_losses   integer,
    conference_ties     integer,
    home_games          integer,
    home_wins           integer,
    home_losses         integer,
    home_ties           integer,
    away_games          integer,
    away_wins           integer,
    away_losses         integer,
    away_ties           integer,
    neutral_games       integer,
    neutral_wins        integer,
    neutral_losses      integer,
    neutral_ties        integer,
    regular_games       integer,
    regular_wins        integer,
    regular_losses      integer,
    regular_ties        integer,
    postseason_games    integer,
    postseason_wins     integer,
    postseason_losses   integer,
    postseason_ties     integer,
    updated_at          timestamptz not null default now(),
    constraint team_record_team_season_key unique (team_id, season)
);

create table public.team_ats (
    id                bigserial primary key,
    team_id           integer      not null,
    season            integer      not null,
    conference        varchar(64),
    games             integer,
    ats_wins          integer,
    ats_losses        integer,
    ats_pushes        integer,
    avg_cover_margin  numeric(5,2),
    fetched_at        timestamptz not null,
    constraint team_ats_team_season_key unique (team_id, season)
);

-- games stored as a JSON string rather than a child table - always read as one
-- whole list, never queried piecemeal, so a jsonb mapping dependency (or a
-- child table with its own migration) buys nothing here.
create table public.team_matchup (
    id            bigserial primary key,
    team_a_id     integer      not null,
    team_b_id     integer      not null,
    team_a_wins   integer,
    team_b_wins   integer,
    ties          integer,
    games         text        not null,
    fetched_at    timestamptz not null,
    constraint team_matchup_pair_key unique (team_a_id, team_b_id)
);

-- Singleton row: the application always upserts id=1. Persisted rather than
-- held in memory so the "once a day" throttle survives this service
-- restarting after Render spins it down for inactivity.
create table public.cfbd_quota_snapshot (
    id               smallint    primary key default 1,
    fetched_at       timestamptz not null,
    monthly_limit    integer     not null,
    used_calls       integer     not null,
    remaining_calls  integer     not null,
    reset_at         timestamptz,
    constraint cfbd_quota_snapshot_singleton check (id = 1)
);

alter table public.team_record         enable row level security;
alter table public.team_ats            enable row level security;
alter table public.team_matchup        enable row level security;
alter table public.cfbd_quota_snapshot enable row level security;
