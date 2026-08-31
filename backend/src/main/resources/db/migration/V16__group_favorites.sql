-- ---------------------------------------------------------------------------
-- Favourite groups.
--
-- A member in a dozen leagues still plays two of them daily. The group picker
-- shows favourites first so switching is one click rather than a search, and
-- the flag lives on the membership because it is a property of this member's
-- relationship to this group - not of the group, which is the same group to
-- everybody else.
-- ---------------------------------------------------------------------------
alter table public.group_member
    add column favorite boolean not null default false;

-- "My favourites" is the read, and it is per member.
create index group_member_favorite_idx
    on public.group_member (user_id) where favorite;
