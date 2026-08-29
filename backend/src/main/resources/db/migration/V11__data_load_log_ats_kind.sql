-- ATS is now refreshed by an explicit admin load rather than on demand from
-- a page view, so it needs to be a loggable kind like the rest. V10's check
-- constraint is a fixed list, so it has to be replaced rather than extended.
alter table public.data_load_log
    drop constraint if exists data_load_log_kind_check;

alter table public.data_load_log
    add constraint data_load_log_kind_check
        check (kind in ('REFERENCE', 'GAMES', 'SCORES', 'RANKINGS', 'ROSTER', 'ATS'));
