-- ---------------------------------------------------------------------------
-- When a group starts, and whether it stops taking members once it has.
--
-- Settlement had no lower bound. It builds the season's periods from the game
-- schedule and charges every one that has closed without being settled, so a
-- group created in October faced a backlog of every week since August - each
-- one charging every member the minimum they never had a chance to pick.
--
-- For a pick'em that is an unearned pile of losses. For an elimination pool it
-- is fatal: at two strikes everyone is out before the first pick is made, and
-- there was no way to start one mid-season at all.
--
-- start_season did not help. It is a year, not a date, and nothing read it.
--
-- The date is a game day, not a timestamp. Periods are game days or game
-- weeks - see CadencePeriod - so a group starting "on the 12th" means the 12th
-- as the schedule reckons it, and an hour of the morning would be a precision
-- the rest of the model cannot honour.
-- ---------------------------------------------------------------------------

alter table public.pick_group
    add column starts_on date,
    -- Off by default: a league that quietly stopped accepting members the day
    -- it began would be a surprise to every group that already exists.
    add column joins_close_at_start boolean not null default false;

-- Existing groups start the day they were created. Not today's date, which is
-- what the column default would have given them - that would claim a group
-- made in August began in September and silently change which periods it is
-- answerable for.
--
-- No period is re-charged either way: settlement records what it has closed
-- and skips it, so this only bounds periods still to come.
update public.pick_group set starts_on = created_at::date where starts_on is null;

alter table public.pick_group alter column starts_on set not null;
alter table public.pick_group alter column starts_on set default current_date;
