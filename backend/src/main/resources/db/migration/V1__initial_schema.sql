-- Nick's Picks - college football pick'em against the spread.
--
-- Replaces the throwaway demo table from the original scaffold.
drop table if exists public.picks;

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Users. id is the Supabase auth user id (the JWT "sub" claim); rows are
-- created on a member's first authenticated request, so there is no separate
-- registration flow to keep in sync with Supabase.
-- ---------------------------------------------------------------------------
create table public.app_user (
    id           uuid primary key,
    email        varchar(320) not null,
    display_name varchar(60)  not null,
    created_at   timestamptz  not null default now()
);

create unique index app_user_display_name_key on public.app_user (lower(display_name));

-- ---------------------------------------------------------------------------
-- Reference data from the CollegeFootballData API. Persisted rather than
-- proxied: the free tier allows 1,000 calls/month and detail pages are
-- user-triggered, so serving them from our own tables is what keeps a few
-- members clicking around from exhausting the quota.
-- ---------------------------------------------------------------------------
create table public.team (
    id              integer primary key,          -- CFBD team id
    school          varchar(120) not null,
    mascot          varchar(80),
    abbreviation    varchar(16),
    conference      varchar(80),
    division        varchar(80),
    classification  varchar(16),
    color           varchar(16),
    alternate_color varchar(16),
    logo_url        text,
    logo_dark_url   text,
    twitter         varchar(80),
    venue_name      varchar(160),
    venue_city      varchar(120),
    venue_state     varchar(40),
    venue_capacity  integer,
    updated_at      timestamptz not null default now()
);

create index team_school_idx on public.team (lower(school));
create index team_conference_idx on public.team (conference);

-- Roster entries are per season, so the same athlete appears once per year.
create table public.athlete (
    id           varchar(24) not null,            -- CFBD athlete id (a string, not a number)
    season       integer     not null,
    first_name   varchar(80),
    last_name    varchar(80),
    team_id      integer references public.team (id),
    team_school  varchar(120),
    position     varchar(8),
    jersey       integer,
    height       integer,                         -- inches
    weight       integer,                         -- pounds
    year         integer,                         -- class: 1=FR .. 5
    home_city    varchar(120),
    home_state   varchar(40),
    home_country varchar(60),
    updated_at   timestamptz not null default now(),
    primary key (id, season)
);

create index athlete_team_season_idx on public.athlete (team_id, season);
create index athlete_name_idx on public.athlete (lower(last_name), lower(first_name));

create table public.coach (
    id         integer primary key,               -- CFBD coach id
    first_name varchar(80),
    last_name  varchar(80),
    hire_date  timestamptz,
    updated_at timestamptz not null default now()
);

-- One row per coach per season per school - drives the career table on a
-- coach's page.
create table public.coach_season (
    coach_id    integer not null references public.coach (id) on delete cascade,
    season      integer not null,
    team_id     integer references public.team (id),
    school      varchar(120),
    conference  varchar(80),
    games       integer,
    wins        integer,
    losses      integer,
    ties        integer,
    sp_overall  numeric(6,2),
    sp_offense  numeric(6,2),
    sp_defense  numeric(6,2),
    primary key (coach_id, season, school)
);

create index coach_season_team_idx on public.coach_season (team_id, season);

-- ---------------------------------------------------------------------------
-- Games. home_spread is from the home team's perspective: -7.5 means the home
-- team is favored by 7.5. Null until a line posts, and a game with no line is
-- not pickable.
--
-- start_time_tbd marks games whose kickoff has not been scheduled. Those are
-- not pickable either - a 30-minute lock is meaningless without a real time.
-- ---------------------------------------------------------------------------
create table public.game (
    id                bigint primary key,         -- CFBD game id
    season            integer      not null,
    week              integer      not null,
    season_type       varchar(16)  not null default 'regular',
    home_team_id      integer references public.team (id),
    home_team         varchar(120) not null,
    home_conference   varchar(80),
    away_team_id      integer references public.team (id),
    away_team         varchar(120) not null,
    away_conference   varchar(80),
    neutral_site      boolean      not null default false,
    conference_game   boolean      not null default false,
    venue             varchar(160),
    kickoff           timestamptz  not null,
    start_time_tbd    boolean      not null default false,
    home_spread       numeric(4,1),
    spread_open       numeric(4,1),
    over_under        numeric(4,1),
    home_moneyline    integer,
    away_moneyline    integer,
    spread_provider   varchar(60),
    spread_updated_at timestamptz,
    home_score        integer,
    away_score        integer,
    home_pregame_elo  integer,
    away_pregame_elo  integer,
    status            varchar(16)  not null default 'SCHEDULED'
                      check (status in ('SCHEDULED', 'IN_PROGRESS', 'FINAL', 'CANCELED')),
    updated_at        timestamptz  not null default now()
);

create index game_season_week_idx on public.game (season, week);
create index game_kickoff_idx on public.game (kickoff);
create index game_home_team_idx on public.game (home_team_id);
create index game_away_team_idx on public.game (away_team_id);

-- ---------------------------------------------------------------------------
-- One row per user per week. Exists to make the 10-pick cap atomic: counting
-- picks cannot prevent two concurrent inserts from both passing the check
-- (the conflicting row does not exist yet, so SELECT ... FOR UPDATE has
-- nothing to lock). Every pick mutation locks this row first; the check
-- constraint is the last line of defence.
-- ---------------------------------------------------------------------------
create table public.weekly_entry (
    user_id    uuid        not null references public.app_user (id) on delete cascade,
    season     integer     not null,
    week       integer     not null,
    pick_count integer     not null default 0 check (pick_count between 0 and 10),
    updated_at timestamptz not null default now(),
    primary key (user_id, season, week)
);

-- ---------------------------------------------------------------------------
-- Picks. locked_spread is copied from the game when the pick is made or
-- edited, so later line movement never changes how an existing pick grades.
-- ---------------------------------------------------------------------------
create table public.pick (
    id            uuid         primary key default gen_random_uuid(),
    user_id       uuid         not null references public.app_user (id) on delete cascade,
    game_id       bigint       not null references public.game (id) on delete cascade,
    selection     varchar(4)   not null check (selection in ('HOME', 'AWAY')),
    locked_spread numeric(4,1) not null,
    result        varchar(8)   not null default 'PENDING'
                  check (result in ('PENDING', 'WIN', 'LOSS', 'PUSH', 'VOID')),
    graded_at     timestamptz,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    unique (user_id, game_id)
);

create index pick_game_idx on public.pick (game_id);
create index pick_user_result_idx on public.pick (user_id, result);

-- ---------------------------------------------------------------------------
-- CFBD bookkeeping.
--
-- cfbd_sync records what has already been fetched, so visiting a team page
-- twice never costs a second API call. cfbd_call_log makes quota consumption
-- a SQL query rather than a guess.
-- ---------------------------------------------------------------------------
create table public.cfbd_sync (
    resource  varchar(40)  not null,
    sync_key  varchar(120) not null,
    synced_at timestamptz  not null default now(),
    primary key (resource, sync_key)
);

create table public.cfbd_call_log (
    id        bigserial primary key,
    endpoint  varchar(160) not null,
    status    integer,
    called_at timestamptz  not null default now()
);

create index cfbd_call_log_called_at_idx on public.cfbd_call_log (called_at desc);

-- ---------------------------------------------------------------------------
-- Season standings. Ranking lives here and nowhere else, so changing the
-- ordering later is a one-file change.
--
-- Ranked by total wins, then fewest losses. VOID and PENDING picks are
-- excluded from every count.
-- ---------------------------------------------------------------------------
create view public.v_standings as
select u.id                                        as user_id,
       u.display_name                              as display_name,
       g.season                                    as season,
       count(*) filter (where p.result = 'WIN')    as wins,
       count(*) filter (where p.result = 'LOSS')   as losses,
       count(*) filter (where p.result = 'PUSH')   as pushes,
       count(*)                                    as games_graded
from public.app_user u
         join public.pick p on p.user_id = u.id
         join public.game g on g.id = p.game_id
where p.result in ('WIN', 'LOSS', 'PUSH')
group by u.id, u.display_name, g.season;

-- ---------------------------------------------------------------------------
-- All access goes through the API, which connects as the postgres role and
-- bypasses RLS. Enabling it with no policies means the public anon key can
-- read nothing if it ever leaks.
-- ---------------------------------------------------------------------------
alter table public.app_user      enable row level security;
alter table public.team          enable row level security;
alter table public.athlete       enable row level security;
alter table public.coach         enable row level security;
alter table public.coach_season  enable row level security;
alter table public.game          enable row level security;
alter table public.pick          enable row level security;
alter table public.weekly_entry  enable row level security;
alter table public.cfbd_sync     enable row level security;
alter table public.cfbd_call_log enable row level security;
