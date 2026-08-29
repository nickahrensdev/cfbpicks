-- The team table is a cache of FBS programs only - that is one API call a
-- season, where ingesting every FCS/D2/D3 program is not.
--
-- But FBS teams play non-FBS opponents constantly (week 1 2026 alone has
-- Iowa State vs Southeast Missouri State), and coaches have earlier careers at
-- schools outside FBS. A foreign key asserts the team table is complete, which
-- it deliberately is not, so ingest fails on the first such row.
--
-- Keep the id column - it is still correct and useful, and DtoMapper resolves
-- it to null when we have no matching row, which the UI already renders as
-- plain text instead of a dead link.
alter table public.game drop constraint if exists game_home_team_id_fkey;
alter table public.game drop constraint if exists game_away_team_id_fkey;

alter table public.coach_season drop constraint if exists coach_season_team_id_fkey;

alter table public.athlete drop constraint if exists athlete_team_id_fkey;
