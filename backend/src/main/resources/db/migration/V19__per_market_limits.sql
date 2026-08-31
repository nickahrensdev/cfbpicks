-- ---------------------------------------------------------------------------
-- Per-market pick limits.
--
-- A group with all three markets on and one scoring table for all of them has
-- a dominant strategy: take heavy favourites to win, every time, and never
-- touch a spread. The overall max picks per cadence does nothing about that,
-- because it does not care which market the picks came from.
--
-- These six columns let a group say "at most 3 winners" or "at least 3
-- spreads" per period, which is what makes the other markets worth playing.
--
-- Null means no limit, for both halves - the same convention
-- max_picks_per_cadence already uses, and the reason none of these can be NOT
-- NULL with a default: 0 is a meaningful maximum (that market is effectively
-- off) and would be the wrong thing to backfill.
--
-- Only the maximums are enforced by this migration's code. A minimum cannot be
-- judged when a pick is made - a member with no picks yet has not broken it -
-- so it is only decidable once the period closes, which is a later slice.
-- ---------------------------------------------------------------------------

alter table public.pick_group
    add column winner_min_per_cadence integer,
    add column winner_max_per_cadence integer,
    add column spread_min_per_cadence integer,
    add column spread_max_per_cadence integer,
    add column total_min_per_cadence  integer,
    add column total_max_per_cadence  integer;

-- A minimum of 0 is the same as none, and allowed rather than rejected so a
-- form that writes 0 for "no minimum" does not fail. A maximum of 0 is a real
-- setting, so maximums are bounded below by 0 and not by 1.
alter table public.pick_group
    add constraint pick_group_winner_min_check
        check (winner_min_per_cadence is null or winner_min_per_cadence >= 0),
    add constraint pick_group_winner_max_check
        check (winner_max_per_cadence is null or winner_max_per_cadence >= 0),
    add constraint pick_group_spread_min_check
        check (spread_min_per_cadence is null or spread_min_per_cadence >= 0),
    add constraint pick_group_spread_max_check
        check (spread_max_per_cadence is null or spread_max_per_cadence >= 0),
    add constraint pick_group_total_min_check
        check (total_min_per_cadence is null or total_min_per_cadence >= 0),
    add constraint pick_group_total_max_check
        check (total_max_per_cadence is null or total_max_per_cadence >= 0);

-- A minimum above the maximum is unsatisfiable: every period would close with
-- the member in breach no matter what they picked.
alter table public.pick_group
    add constraint pick_group_winner_range_check
        check (winner_min_per_cadence is null or winner_max_per_cadence is null
               or winner_min_per_cadence <= winner_max_per_cadence),
    add constraint pick_group_spread_range_check
        check (spread_min_per_cadence is null or spread_max_per_cadence is null
               or spread_min_per_cadence <= spread_max_per_cadence),
    add constraint pick_group_total_range_check
        check (total_min_per_cadence is null or total_max_per_cadence is null
               or total_min_per_cadence <= total_max_per_cadence);
