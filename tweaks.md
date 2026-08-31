# Implement the below changes

Status: `[x]` shipped · `[~]` shipped then revised by a later decision · `[ ]` planned,
with the slice that covers it.

- [x] anywhere a users display name is shown, add an @ before it

- [x] restrict usernames at signup with following, unique, no spaces
  — first shipped against `app_user.display_name`; **superseded and re-shipped** as the
  display-name / username split in the "sign up" item below (V18).

- [x] ability for a user to favorite a group.
- [x] Instead of group selection being in the navigation bar, add it to the sticky header on every page. Display the group name by :creator username. Right align a button that says change group.  when clicked display a model for selecting a group. show favorited groups (max 5) first. have a table with a search bar for finding the group to switch to
  — later revised in conversation: the creator moved off the bar, the button became an icon, the
  name opens a group-info modal, and favourites sort first in one list rather than a separate block.

## /groups/:id
- [x] move the members table  to a tab.

## sign up
- [x] **Slice 7 — Identity.** I want users to create DisplayName and Username. Display name can match another users but username is unique. Keep these to less than 20 characters. Leaderboards should show Display Name (@username)

## nav bar / menu
- [x] **Slice 7 — Identity.** on mobile devices, instead of showing the username, change it to My Profile. allow users to change their display name and username on profile page.



## /admin/groups
- [x] I want the default values to be based on the Group type. If pick'em, weekly, per-year, picks close 30, most picks per week = blank. for elimination: daily, per-year, picks close 30, most picks per day = 1

- [x] **Slices 8 and 9.** I want to add more complexity. I want group creation to have more configurations. depending on step 3 scoreing, I want to add to step 3 where a user can define minimum and maximum limits per cadence. the idea is you can have a group that lets users pick winners, spread, and o/u. if points calculations are the same across the board, there is no incentive to pick anything besides winners that are heavy favorites. This will allow group creators to force a minimum of 3 spreads per cadence or 3 max winners for instance.
  — **Slice 8 (V19)**: per-market min/max inputs on step 3; maximums enforced when a pick is made.
  **Slice 9 (V20)**: minimums are settled once a period's last game kicks off, and whatever the
  member finished short is charged as losses on the leaderboard.

- [x] visibility shouldnt prevent password setting. someone might share a link with multiple people and someone might not be authorized to enter still.

- [x] **Slice 10 — Sharing & referrals.** for group sharing: make this a button next to any public group that someone has joined. for private groups, have a setting at creation and editable after creation that allows the group to be shared by members. Only then will it show up for other members in the group page. Each time a user clicks the share button, copy a url that points the intended user to the website. It should contain some way of identifing the user clicking the share button. I want to track who is sharing groups and furthermore, who is signing up using the referral link. If a link is shared with a user that isnt authenticated, it should take them to the login screen. if the user logs in and they are part of the group, it should take them to the groups leaderboard page. If a user logs in and is not part of the group, it should show them a join group page (with a textbox for a password if required by the group). If a user doesnt have an account, they should be taken to a sign up page and upon completing sign up, taken to the respective groups join page (same password textbox if needed).

## /admin/members
- [x] **Slice 11.** add a coulmn for how many groups a user has created and another for how many they are part of. I also want how many people they have gotten to sign up (via tracked referrals)

## /members/:id
- [~] change the url to /admin/members/:id
  — done, then **reverted at your request** so any signed-in member can open a card. Stays at
  `/members/:id`.
- [x] i am not seeing any of the users picks that they have made.
  — the page defaulted to the current week, which had moved past the last week anyone picked. Now
  defaults to all weeks.
- [x] **Slice 11.** I want to see a groups table that shows the groups a user is part of and details on it.
  — scoped to the groups the viewer is also in: a card is readable by anyone signed in, and which
  other leagues someone plays in is not public just because their picks in a shared one are.

## /leaderboard
- [x] show a note on the leaderboard page of how the selected groups pts are calculated

## page footer
- [x] game data from and picks lock 30 min message
  — now shows the selected group's own lock lead rather than a hardcoded 30.

## game details page
- [x] **Slice 7.** member picks section: abbreviate market name with SPR, O/U, WIN. For pick column, show abbreviation for team name. for line column dont show the O or U for over or under. remove the result column and color code the row with green, red, gray(push), yellow (pending)

- [x] **Slice 7.** around the game: removed. It carried ESPN's per-game ATS summary plus
  attendance; the season-long ATS table above it covers the useful half.

- [x] **Slice 7.** ats section: dont show text for avg cover. just show the word avg.

---

## Not on this list, but already built

Raised in conversation rather than here, and shipped:

- Groups themselves: creation, settings, search, join, membership, delete
- Group-scoped picks and leaderboards
- Co-owners, the creator badge, promotion/demotion, and the last-owner guard
- Join approval with an owners-only Requests tab
- The WINNER market end to end
- A daily group's board is a day, with arrows that skip to the next day that has games
- Games toolbar rework, event-status filter, game count above the board
- Buttons no longer keep their focus tint after a pointer click
- Leaderboard: handle on its own line under the name, no "you" badge, no Win % column
- Game details: team logos as the Team stats column headers, and a much fainter result tint
  (Bootstrap's `.table-*` row variants also force dark text, which was unreadable on a dark card)

## Remaining slices

Everything still open above, in the order it will be built. Each slice is one deployable
change with its own migration where it needs one.

~~**Slice 8 — Pick-time limits.**~~ **Shipped.** Per-market maximums per period, the per-team
pick limit (counted per season) and `multiplePicksPerGame` are all enforced when a pick is
made; group creation grew the per-market min/max inputs on step 3.

~~**Slice 9 — Cadence-close rules.**~~ **Shipped.** A period is settled once its last game
kicks off; whoever finished short of a minimum is charged the difference as losses, which
count on the leaderboard and toward elimination. The budget bar now says what a period
still needs rather than only what it has spent.

~~**Slice 10 — Sharing & referrals.**~~ **Shipped (V21).** One durable link per person per
group; `shareableByMembers` gates it for private groups; `/join/:token` explains the
invitation before asking for an account and carries the token through login and signup;
referral credit is recorded once per person, for ever.

~~**Slice 11 — Admin surface.**~~ **Shipped.** `/admin/members` gained Created / Joined /
Referred columns, `/members/:id` gained a table of the groups you share with that member,
and the add-member dropdown became a searched picker.

**Every slice is done.** Nothing on this list is outstanding.

### Follow-ups after the slices, from reading the picks bar back

- [x] The overall minimum picks per period is no longer elimination-only. It was, back when the
  only consequence of missing one was being knocked out; now an unmet minimum is charged as
  losses, which a points league carries perfectly well - and per-market minimums on the Scoring
  step already worked that way for both types. Strikes stay elimination-only.
- [x] Dropped a dead branch in the picks bar (`max N per day`), unreachable since daily groups
  got a real countdown in Slice 9.
- [x] The picks bar shows a daily group's date as "Sat, Sep 5" rather than "2026-09-05".

## Everything configurable is now enforced

Nothing on the group settings form stores without acting any more. The last three —
elimination strikes, minimum picks per cadence, and the per-market minimums — landed with
Slice 9.


## /admin/groups/:id
- [x] **Slice 11 — Admin surface.** adding a user to a group as an admin: the dropdown to select a user
  is not ideal as this app can scale and have many users. Come up with a better way to find a user to
  add. Modal is an option if thats easiest
  — replaced with a debounced search box over a server-side query, capped at twenty, with existing
  members filtered out server-side. The page no longer downloads every account.

- [x] when a group name changes, the change doesnt get reflected across the app.
  — the page updated its own copy but not `GroupProvider`'s, which is what the group bar, the switcher
  and every page's scoping actually read. Both save handlers now refresh it.