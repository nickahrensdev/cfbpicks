-- ---------------------------------------------------------------------------
-- A second pick market: over/under on the game total.
--
-- The data has been there all along - game.over_under arrives in the same
-- /lines call as the spread - so this migration is entirely about letting a
-- pick say which market it belongs to.
--
-- Existing rows are all spread picks and backfill accordingly.
-- ---------------------------------------------------------------------------

-- 'UNDER' is five characters; the column was sized for HOME/AWAY.
alter table public.pick drop constraint if exists pick_selection_check;
alter table public.pick alter column selection type varchar(8);

alter table public.pick add column market varchar(8) not null default 'SPREAD';
-- Default was only for the backfill. Make the application say what it means.
alter table public.pick alter column market drop default;

-- locked_spread will hold a total for half these rows, and the old name would
-- be actively misleading.
alter table public.pick rename column locked_spread to locked_line;

-- One pick per market per game, rather than one pick per game.
alter table public.pick drop constraint if exists pick_user_id_game_id_key;
alter table public.pick add constraint pick_user_game_market_key
    unique (user_id, game_id, market);

-- Market is derived from selection in Java, so the two can never disagree
-- there. This is the backstop for anything that writes to the table directly.
alter table public.pick add constraint pick_market_selection_check
    check ((market = 'SPREAD' and selection in ('HOME', 'AWAY'))
        or (market = 'TOTAL'  and selection in ('OVER', 'UNDER')));

-- ---------------------------------------------------------------------------
-- The audit trail records the same two facts, so it needs the same widening.
-- ---------------------------------------------------------------------------
alter table public.pick_audit drop constraint if exists pick_audit_selection_check;
alter table public.pick_audit alter column selection type varchar(8);
alter table public.pick_audit alter column previous_selection type varchar(8);

alter table public.pick_audit add column market varchar(8) not null default 'SPREAD';
alter table public.pick_audit alter column market drop default;

alter table public.pick_audit rename column locked_spread to locked_line;
alter table public.pick_audit rename column previous_locked_spread to previous_locked_line;

-- ---------------------------------------------------------------------------
-- The opening total, for parity with spread_open. Already in the /lines
-- response; it simply was never mapped.
-- ---------------------------------------------------------------------------
alter table public.game add column over_under_open numeric(4,1);
