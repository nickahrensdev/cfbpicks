-- Every manual data-load trigger from the admin Data page, run in the
-- background now rather than blocking the request - this is what lets an
-- admin see it happened (and whether it succeeded) instead of just staring
-- at a spinner.
--
-- triggered_by_name is a snapshot rather than a join to app_user: this is an
-- operational record of what ran, not personal data, so it must survive an
-- admin account being deleted later.
create table public.data_load_log (
    id                bigserial primary key,
    kind              varchar(16)  not null
        check (kind in ('REFERENCE', 'GAMES', 'SCORES', 'RANKINGS', 'ROSTER')),
    season            integer,
    parts             varchar(64),
    team_id           integer,
    triggered_by      uuid,
    triggered_by_name varchar(120) not null,
    status            varchar(16)  not null default 'RUNNING'
        check (status in ('RUNNING', 'SUCCESS', 'FAILURE')),
    result_summary    varchar(500),
    error_message     varchar(1000),
    started_at        timestamptz  not null default now(),
    finished_at       timestamptz
);

create index data_load_log_started_idx on public.data_load_log (started_at desc);

alter table public.data_load_log enable row level security;
