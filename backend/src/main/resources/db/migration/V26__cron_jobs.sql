-- ---------------------------------------------------------------------------
-- Scheduled jobs, switchable without a deploy.
--
-- The schedule itself lives outside the app - Supabase pg_cron calls
-- /api/cron/*, the same way it already drives the ESPN score poll. What lives
-- here is whether the app should act when called, which is the half an admin
-- needs to change from a page rather than by editing an environment variable
-- and waiting for Render to rebuild.
--
-- So "stopped" means the schedule still fires and the endpoint declines. That
-- is deliberate: the alternative is the app reaching into pg_cron to rewrite
-- its own schedule, which needs elevated database rights and leaves the two
-- systems able to disagree about what is running.
--
-- app.cron.lines-enabled is replaced by the row below. A property and a table
-- saying the same thing is a contradiction waiting to happen, and only one of
-- them can be changed from the UI.
-- ---------------------------------------------------------------------------

create table public.cron_job (
    name             varchar(40)  primary key,

    enabled          boolean      not null default false,

    -- How often the external schedule calls this, in seconds. The app does not
    -- enforce it - pg_cron does - but it is stored so the picks board can say
    -- when the next refresh is due without anyone hardcoding "30 minutes" in
    -- the frontend. Keep it in step with the pg_cron entry.
    interval_seconds integer      not null check (interval_seconds > 0),

    last_run_at      timestamptz,
    last_status      varchar(12)  check (last_status in ('SUCCESS', 'FAILED', 'SKIPPED')),
    -- What happened, in one line: how many games were touched, or why not.
    last_detail      varchar(500),

    updated_at       timestamptz  not null default now()
);

alter table public.cron_job enable row level security;

-- Off, like the property it replaces. Turning it on is a decision made on the
-- admin page, and every call it makes spends CFBD quota.
insert into public.cron_job (name, enabled, interval_seconds)
values ('lines', false, 1800);

-- ---------------------------------------------------------------------------
-- Line refreshes join the load log, so a cron run is visible in the same place
-- as the manual buttons rather than in a channel of its own.
-- ---------------------------------------------------------------------------
alter table public.data_load_log
    drop constraint if exists data_load_log_kind_check;

alter table public.data_load_log
    add constraint data_load_log_kind_check
        check (kind in ('REFERENCE', 'GAMES', 'SCORES', 'RANKINGS', 'ROSTER', 'ATS', 'LINES'));

-- A cron run has no admin behind it. The column was written by a person until
-- now, so it was not null; it stays not null and carries a label instead, which
-- keeps every reader of the log free of a null check.
