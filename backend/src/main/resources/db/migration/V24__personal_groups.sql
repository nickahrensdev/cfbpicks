-- ---------------------------------------------------------------------------
-- A private board of one, created with every account.
--
-- Every board in the app is a group's board, so a member who has not joined a
-- league has nothing to pick at all - the games page renders an invitation to
-- find a group instead of a schedule. That makes the first run of the app
-- empty for exactly the people still deciding whether they want it.
--
-- A personal group fixes that without inventing a second, group-less way to
-- pick: it is an ordinary group by every rule the pick path already enforces,
-- with one member who is also its owner. Nothing in PickService, grading or
-- the leaderboard needs to know it is special.
--
-- What the flag buys is refusal. A personal group cannot be joined, shared,
-- edited, renamed or deleted - see GroupService, which is where all of that is
-- enforced. The column exists so those checks have something to ask.
-- ---------------------------------------------------------------------------

alter table public.pick_group
    add column personal boolean not null default false;

-- One each, at most. The application creates these on first sight of an
-- account (CurrentUserService), and a request arriving twice concurrently
-- would otherwise be able to make two - a second board that would sit in the
-- group picker with the same name and no way to tell them apart.
--
-- Partial, so it constrains only the personal rows: an ordinary group's
-- creator may of course make as many as they like.
create unique index pick_group_personal_owner_idx
    on public.pick_group (created_by)
    where personal;

-- ---------------------------------------------------------------------------
-- Backfill: one for every account that already exists.
--
-- Without this, only accounts created after this migration would have a board
-- of their own, and every current member would be left in the state this
-- feature exists to remove.
--
-- The settings are the same ones PersonalGroups builds in Java. They are
-- duplicated here rather than shared because a migration has to keep working
-- against the schema as it was at the time it was written - a later change to
-- the Java defaults must not retroactively rewrite boards that already exist.
--
-- Moneyline pays half a spread and costs half on a loss: the spread and the
-- total are priced as coin flips, but a heavy favourite's moneyline is not,
-- and with no pick limit here a flat score would make "take every favourite"
-- the only sensible strategy. See PersonalGroups for the arithmetic.
-- ---------------------------------------------------------------------------

with created as (
    insert into public.pick_group (
        name, description, visibility, join_password, created_by,
        group_type, cadence, length_type, start_season,
        lock_lead_minutes, max_picks_per_cadence, min_picks_per_cadence,
        multiple_picks_per_game, require_approval, shareable_by_members,
        personal,
        moneyline_enabled, spread_enabled, total_enabled,
        moneyline_win_points, moneyline_loss_points, moneyline_push_points,
        spread_win_points, spread_loss_points, spread_push_points,
        total_win_points, total_loss_points, total_push_points,
        strikes_allowed, team_pick_limit, team_pick_limit_scope
    )
    select
        'My Board',
        'Your own board. Nobody else can join it.',
        'PRIVATE',
        null,
        u.id,
        'PICKEM', 'WEEKLY', 'CONTINUOUS', extract(year from now())::int,
        5,      -- picks close five minutes before each kickoff
        null,   -- no maximum
        0,      -- no minimum
        true,   -- both markets on one game is fine
        false,  -- nothing to approve; nobody can join
        false,  -- nothing to share
        true,
        true, true, true,
        0.5, -0.5, 0,
        1.0,  0.0, 0.5,
        1.0,  0.0, 0.5,
        null, null, null
    from public.app_user u
    -- Re-runnable in spirit: skip anyone who somehow already has one.
    where not exists (
        select 1 from public.pick_group g
         where g.created_by = u.id and g.personal
    )
    returning id, created_by
)
insert into public.group_member (group_id, user_id, role)
select id, created_by, 'OWNER' from created;
