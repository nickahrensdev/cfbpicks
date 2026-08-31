-- ---------------------------------------------------------------------------
-- A third market: pick the team to win outright.
--
-- The rule is the simplest of the three - compare the final scores, no number
-- involved - but that is exactly what makes it awkward here. The other two
-- markets are played against a line, and the schema assumed every pick has
-- one. Two things follow:
--
--   * Selection gains its own constants rather than reusing HOME and AWAY.
--     Those already mean "this side against the spread", and Selection derives
--     its market from the constant, which is what stops the pair ever
--     disagreeing. HOME_WINNER is eleven characters, so the column widens.
--
--   * locked_line becomes nullable, because a winner pick has nothing to lock.
--     Storing a zero would be a lie that shows up in the audit trail, and the
--     moneyline does not fit: those are integers well past this column's
--     four digits, and most games carry no moneyline at all.
--
-- A check constraint keeps the two facts in step: the line is required for the
-- markets that have one and forbidden for the one that does not.
--
-- Nothing existing changes. Every current pick is SPREAD or TOTAL with a line,
-- and stays exactly as it is.
-- ---------------------------------------------------------------------------

-- 'HOME_WINNER' does not fit the width chosen for 'UNDER'.
alter table public.pick       alter column selection type varchar(16);
alter table public.pick_audit alter column selection type varchar(16);
alter table public.pick_audit alter column previous_selection type varchar(16);

-- The pairing gains a third arm. Market is derived from selection in Java, so
-- the two can never disagree there; this is the backstop for anything writing
-- to the table directly.
alter table public.pick drop constraint if exists pick_market_selection_check;
alter table public.pick add constraint pick_market_selection_check
    check ((market = 'SPREAD' and selection in ('HOME', 'AWAY'))
        or (market = 'TOTAL'  and selection in ('OVER', 'UNDER'))
        or (market = 'WINNER' and selection in ('HOME_WINNER', 'AWAY_WINNER')));

-- A winner pick locks nothing.
alter table public.pick alter column locked_line drop not null;
alter table public.pick add constraint pick_locked_line_check
    check ((market in ('SPREAD', 'TOTAL') and locked_line is not null)
        or (market = 'WINNER' and locked_line is null));

-- The audit trail records the same fact, so it needs the same freedom.
alter table public.pick_audit alter column locked_line drop not null;
