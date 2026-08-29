-- The season calendar, so the week selector can offer weeks that have not
-- been ingested yet. Without this, "available weeks" is derived from the game
-- table and you can never look ahead to a week you have not already loaded.
create table public.season_week (
    season           integer     not null,
    week             integer     not null,
    season_type      varchar(16) not null default 'regular',
    start_date       timestamptz,
    end_date         timestamptz,
    first_game_start timestamptz,
    last_game_start  timestamptz,
    updated_at       timestamptz not null default now(),
    primary key (season, week, season_type)
);

alter table public.season_week enable row level security;
