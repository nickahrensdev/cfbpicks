-- Member roles. Everyone is a MEMBER until promoted; admins see the admin
-- page and may manage users and app data. Promotion happens either through
-- the admin UI or automatically for emails listed in app.admin-emails.
alter table public.app_user
    add column role varchar(16) not null default 'MEMBER'
        check (role in ('MEMBER', 'ADMIN'));

-- ---------------------------------------------------------------------------
-- Immutable audit trail of every pick action: creations, edits (including
-- line re-locks) and cancellations. Rows are only ever inserted.
--
-- Deliberately no FK to pick: a cancelled pick's row is deleted, but its
-- history must survive. user_id keeps its FK so the trail dies with the
-- member (account deletion should not leave orphaned personal data).
-- ---------------------------------------------------------------------------
create table public.pick_audit (
    id                     bigserial primary key,
    pick_id                uuid         not null,
    user_id                uuid         not null references public.app_user (id) on delete cascade,
    game_id                bigint       not null,
    action                 varchar(8)   not null check (action in ('CREATE', 'UPDATE', 'CANCEL')),
    selection              varchar(4)   not null,
    locked_spread          numeric(4,1) not null,
    previous_selection     varchar(4),
    previous_locked_spread numeric(4,1),
    created_at             timestamptz  not null default now()
);

create index pick_audit_user_idx on public.pick_audit (user_id, created_at desc);
create index pick_audit_created_idx on public.pick_audit (created_at desc);

alter table public.pick_audit enable row level security;
