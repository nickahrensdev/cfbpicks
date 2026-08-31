-- ---------------------------------------------------------------------------
-- Groups: isolated picking leagues.
--
-- Until now the site has been one implicit league - every pick, every
-- leaderboard row and every member global. A group carries its own rules
-- (cadence, pick caps, which markets are live, what each outcome is worth) so
-- two sets of friends can play the same season by different rules.
--
-- This migration is deliberately additive. It creates the groups domain and
-- touches nothing that already holds data: pick, weekly_entry and app_user are
-- untouched, so the app behaves exactly as before until the follow-up
-- migration adds group_id to picks and rewires the leaderboard. That keeps the
-- irreversible half of the work (Flyway runs on startup against the live
-- database, and there is no down migration) to a step that cannot lose a row.
--
-- The table is pick_group rather than group because "group" is a SQL reserved
-- word, and LeaderboardService talks to the schema through raw JdbcTemplate
-- SQL - quoting it in every one of those sites would be a permanent tax for a
-- cosmetic gain.
-- ---------------------------------------------------------------------------

create table public.pick_group (
    id                      uuid         primary key default gen_random_uuid(),

    -- Names are not unique. Two sets of friends can both call their league
    -- "The Office"; search disambiguates by owner and description instead.
    name                    varchar(60)  not null,
    description             varchar(500),
    visibility              varchar(8)   not null
                            check (visibility in ('PUBLIC', 'PRIVATE')),

    -- Stored as entered, by an explicit product decision: owners want to read
    -- the password back to share it. Never leaves the API - the search and
    -- detail responses expose only a "password required" boolean.
    join_password           varchar(60),

    -- Cascades with the owner. Deleting a member already destroys their picks,
    -- entries and audit rows the same way, and an ownerless group would have
    -- nobody able to configure or delete it.
    owner_id                uuid         not null
                            references public.app_user (id) on delete cascade,

    group_type              varchar(12)  not null
                            check (group_type in ('PICKEM', 'ELIMINATION')),
    cadence                 varchar(8)   not null
                            check (cadence in ('DAILY', 'WEEKLY')),
    length_type             varchar(12)  not null
                            check (length_type in ('CONTINUOUS', 'PER_YEAR')),
    start_season            integer      not null,

    -- Per-group version of app.pickem.lock-lead-minutes: a game stays pickable
    -- until this many minutes before its own kickoff.
    lock_lead_minutes       integer      not null default 30
                            check (lock_lead_minutes >= 0),

    -- Null means no cap, which is the default the spec asks for.
    max_picks_per_cadence   integer      check (max_picks_per_cadence > 0),
    -- Elimination only: falling short of this in a cadence period eliminates
    -- you. Zero lets members skip a day. Ignored for PICKEM.
    min_picks_per_cadence   integer      not null default 1
                            check (min_picks_per_cadence >= 0),
    -- Whether one game can carry more than one pick, e.g. a spread and a total.
    multiple_picks_per_game boolean      not null default true,

    winner_enabled          boolean      not null,
    spread_enabled          boolean      not null,
    total_enabled           boolean      not null,

    -- Points per outcome, per market. Signed and fractional on purpose: a group
    -- may want -1 for a loss, or half a point for a push.
    winner_win_points       numeric(6,2) not null,
    winner_loss_points      numeric(6,2) not null,
    winner_push_points      numeric(6,2) not null,
    spread_win_points       numeric(6,2) not null,
    spread_loss_points      numeric(6,2) not null,
    spread_push_points      numeric(6,2) not null,
    total_win_points        numeric(6,2) not null,
    total_loss_points       numeric(6,2) not null,
    total_push_points       numeric(6,2) not null,

    -- Elimination only: wrong picks tolerated before a member is out.
    strikes_allowed         integer      check (strikes_allowed >= 0),

    -- Null means a team may be picked any number of times. The scope says
    -- which markets the count applies to.
    team_pick_limit         integer      check (team_pick_limit > 0),
    team_pick_limit_scope   varchar(8)
                            check (team_pick_limit_scope in ('WINNER', 'SPREAD', 'BOTH')),

    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),

    -- A group nobody can pick in is a configuration mistake, not a mode.
    constraint pick_group_market_enabled_check
        check (winner_enabled or spread_enabled or total_enabled),

    constraint pick_group_pick_range_check
        check (max_picks_per_cadence is null
            or min_picks_per_cadence <= max_picks_per_cadence),

    -- A never-ending elimination pool ends the first time everyone is out and
    -- can never start over, so the combination is not offered.
    constraint pick_group_elimination_length_check
        check (group_type <> 'ELIMINATION' or length_type = 'PER_YEAR'),

    constraint pick_group_elimination_strikes_check
        check (group_type <> 'ELIMINATION' or strikes_allowed is not null),

    -- The limit and its scope are meaningless apart.
    constraint pick_group_team_limit_scope_check
        check ((team_pick_limit is null) = (team_pick_limit_scope is null))
);

-- Search only ever reads public groups, and only ever matches on the name.
create index pick_group_visibility_idx on public.pick_group (visibility);
create index pick_group_name_idx on public.pick_group (lower(name));

create table public.group_member (
    group_id  uuid        not null references public.pick_group (id) on delete cascade,
    user_id   uuid        not null references public.app_user (id)   on delete cascade,
    role      varchar(8)  not null default 'MEMBER'
              check (role in ('OWNER', 'MEMBER')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

-- "Which groups am I in" is the hot read; the primary key is the wrong way
-- round for it.
create index group_member_user_idx on public.group_member (user_id);

-- Ownership can move later, but there is exactly one owner at a time. A
-- partial unique index says that without a second table.
create unique index group_member_owner_idx
    on public.group_member (group_id) where role = 'OWNER';

alter table public.pick_group   enable row level security;
alter table public.group_member enable row level security;
