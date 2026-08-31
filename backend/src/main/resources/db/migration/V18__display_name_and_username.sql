-- ---------------------------------------------------------------------------
-- Identity splits in two: a display name and a username.
--
-- One field was doing both jobs and doing neither well. It had to be unique,
-- which meant the second Nick to sign up could not be "Nick"; and it had to be
-- machine-safe enough to render as @nick, which meant no spaces, which is a
-- silly rule for something meant to read as a person's name.
--
--   display_name  what you are called. Duplicates allowed, spaces allowed.
--   username      who you are. Unique, no spaces - this is the @handle.
--
-- Both cap at 20 characters.
--
-- Everyone keeps the name they have: username is seeded from the current
-- display_name, which is already unique and already space-free, so nobody's
-- handle changes. display_name keeps its value too, so both read the same
-- until someone chooses to change one.
-- ---------------------------------------------------------------------------

alter table public.app_user add column username varchar(20);

-- Seed from the existing name, truncated to the new limit.
--
-- Truncation is the one thing that could collide - two members whose names
-- differ only past character 20 - so the numbering below handles it rather
-- than letting the unique index fail the migration. row_number gives the
-- oldest account the clean name and suffixes the rest, and the base is cut to
-- 18 first so the suffix still fits.
update public.app_user u
   set username = case
                    when t.rn = 1 then left(u.display_name, 20)
                    else left(u.display_name, 18) || t.rn
                  end
  from (
        select id,
               row_number() over (
                   partition by lower(left(display_name, 20))
                   order by created_at, id
               ) as rn
          from public.app_user
       ) t
 where u.id = t.id;

alter table public.app_user alter column username set not null;

-- The handle is what has to be unique now. Case-insensitive, matching how the
-- old display-name index worked and how people actually read a name.
drop index if exists public.app_user_display_name_key;
create unique index app_user_username_key on public.app_user (lower(username));

-- Display names were sized for a field doing both jobs.
alter table public.app_user
    alter column display_name type varchar(20) using left(display_name, 20);
