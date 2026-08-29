-- ---------------------------------------------------------------------------
-- Weekly poll rankings.
--
-- /rankings?year=N returns every week and every poll in one call, so this is
-- one API call per season rather than per week.
--
-- Only the three polls the site cares about are stored; the feed also carries
-- FCS, D2 and D3 coaches polls that would just be noise here.
--
-- The key is (season, week, season_type, poll, school) rather than anything
-- involving rank, because polls have ties - two teams can share 25th - so
-- rank is not unique within a poll.
-- ---------------------------------------------------------------------------
create table public.poll_ranking (
    id                bigserial primary key,
    season            integer      not null,
    week              integer      not null,
    season_type       varchar(16)  not null default 'regular',
    poll              varchar(48)  not null,
    rank              integer      not null,
    team_id           integer,
    school            varchar(120) not null,
    conference        varchar(80),
    first_place_votes integer,
    points            integer,
    updated_at        timestamptz  not null default now(),
    unique (season, week, season_type, poll, school)
);

create index poll_ranking_lookup_idx on public.poll_ranking (season, season_type, week, poll);
create index poll_ranking_team_idx on public.poll_ranking (team_id, season);

alter table public.poll_ranking enable row level security;

-- ---------------------------------------------------------------------------
-- Win probability, which the games feed supplies per side.
--
-- These are *postgame* figures - they are null until a game finishes, and
-- describe how the result unfolded rather than predicting it. Stored as they
-- come back (0..1) and rendered as a percentage.
-- ---------------------------------------------------------------------------
alter table public.game
    add column home_postgame_win_probability numeric(7,6),
    add column away_postgame_win_probability numeric(7,6),
    add column excitement_index              numeric(7,4);
