-- ---------------------------------------------------------------------------
-- Sharing a group, and crediting whoever did the sharing.
--
-- Three things, which are easier to read together than apart:
--
--   shareable_by_members  a private group's opt-in. Public groups are already
--                         findable by search, so any member of one may share
--                         it; a private group is private because its owner
--                         chose that, and members must not route around it.
--
--   group_share_link      one durable link per person per group. Durable
--                         because a link someone has already pasted into a
--                         message must keep working - regenerating on every
--                         click would break every link ever sent. The unique
--                         (group_id, sharer_id) is what makes it one.
--
--   group_referral        who brought whom. Keyed by the person referred, not
--                         by a surrogate id, because a person can only be
--                         brought to the site once - a second link they follow
--                         later does not re-credit anyone.
-- ---------------------------------------------------------------------------

alter table public.pick_group
    add column shareable_by_members boolean not null default false;

create table public.group_share_link (
    id         uuid        primary key default gen_random_uuid(),
    group_id   uuid        not null references public.pick_group (id) on delete cascade,
    sharer_id  uuid        not null references public.app_user (id)   on delete cascade,
    -- Random and opaque. It is the whole credential the link carries, so it
    -- must not be guessable from the group id or from anyone's name.
    token      varchar(32) not null,
    created_at timestamptz not null default now(),
    constraint group_share_link_unique unique (group_id, sharer_id)
);

create unique index group_share_link_token_key on public.group_share_link (token);

create table public.group_referral (
    -- One attribution per person, permanently. Whoever's link first brought
    -- them here keeps the credit; a link they follow next month does not
    -- overwrite it, which is what stops referral counts being farmable.
    user_id    uuid        primary key references public.app_user (id) on delete cascade,
    group_id   uuid        not null references public.pick_group (id)  on delete cascade,
    sharer_id  uuid        not null references public.app_user (id)    on delete cascade,
    created_at timestamptz not null default now()
);

create index group_referral_sharer_idx on public.group_referral (sharer_id);

alter table public.group_share_link enable row level security;
alter table public.group_referral   enable row level security;
