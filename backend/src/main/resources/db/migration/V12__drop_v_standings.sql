-- v_standings was a second standings implementation that nothing served.
-- The live leaderboard has always been built by LeaderboardService, and the
-- two had quietly diverged: the view inner-joins picks, so a member holding
-- only voided picks vanished from it, while the service left-joins from
-- app_user and shows them on a zero row. Only a test ever read the view, so
-- the tested ordering was not the ordering members saw.
--
-- Ranking now lives in LeaderboardService and nowhere else: points first
-- (a win 1, a tie 0.5, a loss 0), then most wins, then fewest losses.
drop view if exists public.v_standings;
