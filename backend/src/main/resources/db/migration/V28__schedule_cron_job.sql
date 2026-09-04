-- ---------------------------------------------------------------------------
-- A daily refresh of the season schedule.
--
-- One CFBD call covers the whole season, not just the current week, which is
-- the point: kickoff times move for television weeks ahead of the game, and
-- pick lock windows are computed from kickoff. Refreshing only the current
-- week would mean a game two weeks out kept a stale time until it was nearly
-- upon us - and asking week by week costs a call each for exactly the same
-- rows, so narrowing it would spend more quota, not less.
--
-- Daily at 1 call is 30 a month against a 5,000 limit.
--
-- This job writes no scores and no status. CFBD's /games lags: a game the ESPN
-- poller has already settled can come back completed=false with null points,
-- which would null the score and drop FINAL back to IN_PROGRESS. Nothing
-- repaired that, because the ESPN poller only reconsiders games that kicked
-- off within the last six hours, so a finished game would have stayed on the
-- board as live. Scores are the ESPN poller's, and the manual Scores button's
-- for backfilling an old season.
--
-- Its own row rather than folding into 'stats': this is the game schedule,
-- that is team form, and an admin may well want one without the other.
-- ---------------------------------------------------------------------------

-- Off, like the jobs before it. Turning it on is a decision made on the admin
-- page, and every call it makes spends CFBD quota.
insert into public.cron_job (name, enabled, interval_seconds)
values ('schedule', false, 86400);
