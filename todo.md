# Nick's Picks — To do

> Reference: [documentation.md](documentation.md) for rules and architecture,
> [data-flow.md](data-flow.md) for per-page API calls, database reads and CFBD endpoints.

## Games
- [x] Look ahead at future weeks
  - The season calendar is ingested from `/calendar` (1 API call) into `season_week`, so the
    week selector offers **every** week of the season rather than only the ingested ones.
    Weeks with no games loaded are dimmed; an admin loads one from **Admin → Data**.
- [x] Stop the page refreshing on every pick
  - The affected card and the counter now update in place from the server's response. A full
    reload only happens when the server disagrees with the page (stale lock or pick count).
- [x] Filter games by conference, team and spread size
  - Week picker is a dropdown, defaulting to the current week. Filters sit in a collapsed panel
    with an active-count badge; the spread control is a two-thumb range so you can bound the
    band from both ends (e.g. only games between 3 and 10 points).
- [x] Picks-made bar like My picks, pinned to the top on scroll
  - Sticky beneath the navbar, showing used / max and remaining.

## Leaderboard
- [x] Show all signed-up users, whether or not they have picked
- [x] Week dropdown, defaulting to Overall
- [x] Username, record and picks made, all scoped to the dropdown
- [x] Filter the table by username
- [x] Select a user to view their locked picks (`/members/:id`)

## My picks
- [x] Show the current line beside the line picked against, with a **Take &lt;line&gt;** button when
      it has moved in the user's favour; switch-to button removed
- [x] Merge into the Games tab as an additional filter
  - The **My picks** toggle sits with the other filters; `/my-picks` redirects to `/?mine=1`.
    Game cards now carry the line comparison, the re-lock button and cancel, so the board is
    the only page you need.

## Admin
- [x] Admin page for managing user data and viewing pick logs
  - **Admin → Members**: grant/revoke admin, delete a member
  - **Admin → Activity log**: every pick action
- [x] Restrict Data to the admin role — enforced server-side, not just hidden in the UI

## Logs
- [x] Running activity of all user changes (picks made, cancelled, line updated)
  - Insert-only `pick_audit` table, so a cancelled pick still shows its full history

## Rankings
- [x] Store weekly poll data from `/rankings?year=` — Playoff Committee, AP Top 25, Coaches Poll
  - One API call returns every week and every poll. Manual button on **Admin → Data**, and the
    Sunday job re-runs it as new polls publish.
- [x] Show `#N` before a ranked team's name everywhere
  - One poll per week, in the order above — the committee only publishes from about week 11, so
    earlier weeks fall through to AP. Using a single poll stops the same team reading as #3 on one
    screen and #5 on another.
- [x] Rank follows the selected week on pages that have one; latest poll elsewhere
  - Falls back to the most recent ranked week at or before the one viewed, since polls publish
    *during* a week.
- [x] Ranking history section on the team page, with all polls for the current week

## Games data
- [x] Drop the `week` param from the `/games` load
  - `/games?year=` returns all 888 games of the season in **one call** instead of ~14. Every future
    week is now loaded the moment the season is.
- [x] Store win probability per team
  - `homePostgameWinProbability` / `awayPostgameWinProbability`, plus excitement index. These are
    **postgame** figures — null until a game finishes — so they show on the game detail page only
    once it is final.
- [x] Show the venue on the games tab

## Over/under picks
- [x] Second market: over/under on the game total
  - `pick.market` (`SPREAD` | `TOTAL`), derived from the selection so the two can never disagree,
    with a database check constraint as backstop. `locked_spread` renamed to `locked_line`.
  - One shared allowance of 10 across both markets; both can be picked on the same game.
  - Cost **zero** extra API quota — `game.over_under` already arrived with the spread.
- [x] O/U column on the game card beside SPR

## Other
- [x] Usernames must be unique
  - Case-insensitive, enforced by a database index and editable at `/profile`
- [x] Prevent picks against a stale line
  - The page sends the spread it was showing. If it is no longer current the server rejects the
    pick with `409 LINE_MOVED` and returns the real number; the card refreshes and the member
    picks again knowingly. Comparison is numeric, so `-7.5` and `-7.50` are not a conflict.
- [x] Improve the theming of the app

### Colour scheme
| Swatch | Hex | Used for |
| --- | --- | --- |
| Midnight | `#031926` | Nav, footer, body text |
| Teal | `#468189` | Primary actions |
| Sage | `#77ACA2` | Info, tinted panels |
| Mist | `#9DBEBB` | Muted accents |
| Cream | `#F4E9CD` | Page background |

Danger and warning stay outside the palette deliberately — destructive and time-critical states
need to read as different in kind, not just a different hue.

---

## Known limitations (not bugs)
- **Coach career depth** — CFBD returns only the ingested year's seasons, so career tables are
  thin. One extra API call per additional year.
- **No FCS coaches** — verified: the provider has no coach records for FCS programs at all.
  FCS *rosters* do work.
- **Grading unproven on a real game** — the logic is covered by tests, but no real game has
  finished yet. Week 1 kicks off 29 Aug 2026.
