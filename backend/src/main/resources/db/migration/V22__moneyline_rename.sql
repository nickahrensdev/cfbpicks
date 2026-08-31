-- ---------------------------------------------------------------------------
-- The winner market is renamed to the moneyline.
--
-- Purely a naming change: nothing about how the market is picked, graded or
-- scored is different afterwards. "Moneyline" is what this bet is called
-- everywhere else, and "winner" collided with WIN the pick *result* - a column
-- reading market='WINNER', result='LOSS' invited a double-take every time.
--
--   market      'WINNER'      -> 'MONEYLINE'
--   selection   'HOME_WINNER' -> 'HOME_ML',  'AWAY_WINNER' -> 'AWAY_ML'
--   scope       'WINNER'      -> 'MONEYLINE'
--   pick_group  winner_*      -> moneyline_*
--
-- PickResult.WIN is deliberately untouched. It is the outcome of a pick in any
-- market, not a market of its own, and renaming it to ML would be nonsense.
--
-- 'MONEYLINE' is nine characters, so every varchar(8) holding a market value
-- has to widen before the data can be rewritten. That is why the constraints
-- come off first: a check naming the old values would reject the update, and a
-- column too narrow for the new one would reject it as well.
-- ---------------------------------------------------------------------------

-- Constraints off first - each one names values that are about to change.
alter table public.pick            drop constraint if exists pick_market_selection_check;
alter table public.pick            drop constraint if exists pick_locked_line_check;
alter table public.cadence_penalty drop constraint if exists cadence_penalty_market_check;
alter table public.pick_group      drop constraint if exists pick_group_team_pick_limit_scope_check;

-- Room for the longer word.
alter table public.pick            alter column market type varchar(12);
alter table public.pick_audit      alter column market type varchar(12);
alter table public.cadence_penalty alter column market type varchar(12);
alter table public.pick_group      alter column team_pick_limit_scope type varchar(12);

-- The data itself. pick_audit is rewritten too: it is the trail of what people
-- actually did, and leaving half of it in the old vocabulary would make the
-- activity log read as though two different markets had existed.
update public.pick set market = 'MONEYLINE' where market = 'WINNER';
update public.pick set selection = 'HOME_ML' where selection = 'HOME_WINNER';
update public.pick set selection = 'AWAY_ML' where selection = 'AWAY_WINNER';

update public.pick_audit set market = 'MONEYLINE' where market = 'WINNER';
update public.pick_audit set selection = 'HOME_ML' where selection = 'HOME_WINNER';
update public.pick_audit set selection = 'AWAY_ML' where selection = 'AWAY_WINNER';
update public.pick_audit set previous_selection = 'HOME_ML'
 where previous_selection = 'HOME_WINNER';
update public.pick_audit set previous_selection = 'AWAY_ML'
 where previous_selection = 'AWAY_WINNER';

update public.cadence_penalty set market = 'MONEYLINE' where market = 'WINNER';
update public.pick_group set team_pick_limit_scope = 'MONEYLINE'
 where team_pick_limit_scope = 'WINNER';

-- Constraints back, saying the same things about the new names.
alter table public.pick add constraint pick_market_selection_check
    check ((market = 'SPREAD'    and selection in ('HOME', 'AWAY'))
        or (market = 'TOTAL'     and selection in ('OVER', 'UNDER'))
        or (market = 'MONEYLINE' and selection in ('HOME_ML', 'AWAY_ML')));

-- A moneyline pick is played against the result rather than a number, so it is
-- the one market with no line to lock.
alter table public.pick add constraint pick_locked_line_check
    check ((market in ('SPREAD', 'TOTAL') and locked_line is not null)
        or (market = 'MONEYLINE' and locked_line is null));

alter table public.cadence_penalty add constraint cadence_penalty_market_check
    check (market is null or market in ('SPREAD', 'TOTAL', 'MONEYLINE'));

alter table public.pick_group add constraint pick_group_team_pick_limit_scope_check
    check (team_pick_limit_scope is null
        or team_pick_limit_scope in ('MONEYLINE', 'SPREAD', 'BOTH'));

-- The group's own columns. A rename carries its check constraints with it, so
-- pick_group_market_enabled_check and the per-market range checks keep working
-- untouched - only their names still say winner, fixed below.
alter table public.pick_group rename column winner_enabled         to moneyline_enabled;
alter table public.pick_group rename column winner_win_points      to moneyline_win_points;
alter table public.pick_group rename column winner_loss_points     to moneyline_loss_points;
alter table public.pick_group rename column winner_push_points     to moneyline_push_points;
alter table public.pick_group rename column winner_min_per_cadence to moneyline_min_per_cadence;
alter table public.pick_group rename column winner_max_per_cadence to moneyline_max_per_cadence;

alter table public.pick_group rename constraint pick_group_winner_min_check
    to pick_group_moneyline_min_check;
alter table public.pick_group rename constraint pick_group_winner_max_check
    to pick_group_moneyline_max_check;
alter table public.pick_group rename constraint pick_group_winner_range_check
    to pick_group_moneyline_range_check;
