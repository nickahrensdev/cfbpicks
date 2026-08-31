-- ---------------------------------------------------------------------------
-- Rosters and player pages are served live from ESPN, so nothing about an
-- athlete is stored any more.
--
-- The table only ever existed as a cache for a metered provider: CFBD charged
-- per roster call, so the answer had to be kept. ESPN is unmetered and its
-- client already caches for twelve hours, which is the same freshness a stored
-- copy gave - without a copy that silently froze for a season and could not be
-- refreshed when a player transferred.
--
-- Dropping it also retires the personal data it held. Name, hometown, height,
-- weight and class year for every player on every roster anyone ever opened is
-- a real body of information about real, mostly young, people; keeping it
-- indefinitely was never something the app needed to do.
--
-- The roster sync markers go with it. They recorded "this team is loaded",
-- which is now meaningless - there is nothing to load.
-- ---------------------------------------------------------------------------

drop table if exists public.athlete;

delete from public.cfbd_sync where resource = 'roster';
