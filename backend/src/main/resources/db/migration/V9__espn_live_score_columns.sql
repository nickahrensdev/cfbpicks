-- Current quarter/clock, written only by the ESPN minute-by-minute poller
-- (EspnScoreIngestService). Nullable and cleared once a game reaches FINAL -
-- a finished game has no clock left to show. home_score/away_score are
-- reused as-is for the score itself; "the score" is one fact regardless of
-- which provider reported it.
alter table public.game add column espn_period integer;
alter table public.game add column espn_clock varchar(16);
