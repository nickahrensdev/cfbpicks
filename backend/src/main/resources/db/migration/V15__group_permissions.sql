-- ---------------------------------------------------------------------------
-- Co-owners, and joining by approval.
--
-- Three changes that all follow from one idea: authority over a group stops
-- being "you are the row in pick_group.owner_id" and becomes "you hold the
-- OWNER role in group_member". That lets an owner share the load, which a
-- league of any size needs, and it separates who *made* the group from who
-- currently runs it.
--
-- Existing data survives unchanged: every group already has exactly one OWNER
-- row in group_member (created alongside the group), so dropping the
-- single-owner index and reading authority from that table leaves the same
-- person in charge of the same groups.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Several owners at once.
-- ---------------------------------------------------------------------------
drop index if exists public.group_member_owner_idx;

-- ---------------------------------------------------------------------------
-- owner_id becomes created_by: identity, not authority.
--
-- It now answers only "who made this", which is what the creator badge shows.
-- Two consequences:
--
--   * It is nullable. Deleting the creator's account used to take the whole
--     group with it - reasonable when they were by definition the only person
--     who could run it, indefensible once a group can have other owners. The
--     group now outlives them and simply loses its creator attribution.
--
--   * Nothing reads it for permissions. GroupService asks group_member.
-- ---------------------------------------------------------------------------
alter table public.pick_group drop constraint if exists pick_group_owner_id_fkey;
alter table public.pick_group rename column owner_id to created_by;
alter table public.pick_group alter column created_by drop not null;

alter table public.pick_group
    add constraint pick_group_created_by_fkey
    foreign key (created_by) references public.app_user (id) on delete set null;

-- ---------------------------------------------------------------------------
-- Joining by approval.
--
-- Off by default, so every existing group keeps behaving exactly as it does
-- today. When on, every route in - search or a shared link - produces a
-- request rather than a membership.
-- ---------------------------------------------------------------------------
alter table public.pick_group
    add column require_approval boolean not null default false;

create table public.group_join_request (
    id           uuid        primary key default gen_random_uuid(),
    group_id     uuid        not null references public.pick_group (id) on delete cascade,
    user_id      uuid        not null references public.app_user (id)   on delete cascade,
    status       varchar(8)  not null default 'PENDING'
                 check (status in ('PENDING', 'APPROVED', 'DENIED')),
    requested_at timestamptz not null default now(),
    decided_at   timestamptz,
    -- Kept for the audit trail; survives the decider's account being deleted.
    decided_by   uuid        references public.app_user (id) on delete set null,

    -- One row per person per group, reused rather than accumulated: asking
    -- again after a refusal moves this row back to PENDING, so an owner sees a
    -- request list rather than a history of every attempt.
    unique (group_id, user_id)
);

-- The owners' queue: pending requests for one group.
create index group_join_request_group_idx on public.group_join_request (group_id, status);

alter table public.group_join_request enable row level security;
