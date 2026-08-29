# Data flow reference

What every page calls, what every endpoint reads and writes, and where the CollegeFootballData
data comes from. Companion to [documentation.md](documentation.md), which covers the rules and the
architecture.

Three layers, in order:

1. [Pages → API](#1-pages--api) — what the browser calls
2. [API → database](#2-api--database) — what each endpoint touches
3. [CFBD → database](#3-cfbd--database) — where the raw data comes from

---

## 1. Pages → API

Two calls happen regardless of page:

| When | Call | Why |
| --- | --- | --- |
| Once per sign-in | `GET /api/me` | `ProfileProvider` — display name and role for the nav. Provisions the member row on first sight. |
| Every request | *(none)* | The Supabase token comes from memory; `getSession()` refreshes it without a network call unless it is near expiry. |

### `/login` — [LoginPage.jsx](frontend/src/pages/LoginPage.jsx)

No API calls. Talks only to Supabase Auth (`signInWithPassword` / `signUp`). The backend is not
involved until the first authenticated page.

### `/` and `/?mine=1` — [GamesPage.jsx](frontend/src/pages/GamesPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/weeks/current` |
| Week selected | `GET /api/games?season&week`<br>`GET /api/picks?season&week`<br>`GET /api/games/filters?season&week` — all three in parallel |
| Pick a side | `POST /api/picks` `{gameId, selection, expectedLine}` |
| Change side | `PUT /api/picks/{id}` `{selection, expectedLine}` |
| Tap the chosen side again | `DELETE /api/picks/{id}` |
| Take a better line | `POST /api/picks/{id}/relock` |
| After any of the four | `GET /api/games/{id}` — refreshes just that card |

Filtering (conference, team, spread band, my picks) is applied **in the browser** against the games
already loaded. The server supports the same filters as query parameters, but a round trip per
slider step would make the range control feel broken. The two implementations are kept in step
deliberately; `GameService.matches` is the authority if they ever disagree.

Picking does **not** reload the board. Only the affected card and the pick counter change. A full
reload happens in exactly two cases: the server says the window closed, or it says the weekly cap
is reached — both mean the page's view of the world was stale.

### `/games/:id` — [GameDetailPage.jsx](frontend/src/pages/GameDetailPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/games/{id}` |

One call. Member picks come back empty until the game kicks off — filtered server-side, so the data
is not merely hidden.

### `/leaderboard` — [LeaderboardPage.jsx](frontend/src/pages/LeaderboardPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/weeks/current` — populates the period dropdown |
| Mount, and on period change | `GET /api/leaderboard[?week=]` |

No `week` means Overall. Name search filters in the browser — the whole member list is already
present and it is small.

### `/members/:id` — [MemberPicksPage.jsx](frontend/src/pages/MemberPicksPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/weeks/current` |
| Mount, and on week change | `GET /api/members/{id}/picks?season&week` |

### `/profile` — [ProfilePage.jsx](frontend/src/pages/ProfilePage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | *(none — reads `ProfileProvider`)* |
| Save | `PUT /api/me` `{displayName}`, then `GET /api/me` to refresh the nav |

### `/teams/:id` — [TeamPage.jsx](frontend/src/pages/TeamPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/teams/{id}` |

One call, but see §2 — this is the only read endpoint that can trigger an outbound CFBD request.

### `/athletes/:id`, `/coaches/:id`

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/athletes/{id}` / `GET /api/coaches/{id}` |

### `/admin/members` — [AdminUsersPage.jsx](frontend/src/pages/admin/AdminUsersPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/admin/users` |
| Toggle role | `PUT /api/admin/users/{id}/role`, then re-list |
| Delete | `DELETE /api/admin/users/{id}`, then re-list |

### `/admin/data` — [AdminDataPage.jsx](frontend/src/pages/admin/AdminDataPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount | `GET /api/admin/quota`, `GET /api/weeks/current` |
| Load reference | `POST /api/admin/ingest/reference` |
| Load week | `POST /api/admin/ingest/week?week=` |
| Load scores | `POST /api/admin/ingest/scores?week=` |
| After any ingest | `GET /api/admin/quota`, `GET /api/weeks/current` |

### `/admin/activity` — [ActivityLogPage.jsx](frontend/src/pages/admin/ActivityLogPage.jsx)

| Trigger | Call |
| --- | --- |
| Mount, member/limit change, Refresh | `GET /api/admin/activity?userId&limit` |

---

## 2. API → database

Every authenticated endpoint additionally resolves the caller: `select app_user by id`, inserting on
first sight and updating `role` if the email is in `app.admin-emails`. Not repeated per row below.

### Reads

| Endpoint | Tables read | Notes |
| --- | --- | --- |
| `GET /api/me` | `app_user` | Inserts on first sight; may check `app_user` for a unique default name |
| `GET /api/weeks/current` | `season_week`, `game` | Prefers the calendar; falls back to distinct weeks in `game` |
| `GET /api/games` | `game`, `team`, `pick` | One `team` load for the whole page, not two per game |
| `GET /api/games/filters` | `game`, `team` | Distinct conferences and teams that week, plus the widest line |
| `GET /api/games/{id}` | `game`, `team`, `pick`, `app_user` | `app_user` and all picks only once the game has kicked off |
| `GET /api/picks` | `pick`, `weekly_entry`, `game`, `team` | Games and teams fetched once for the list |
| `GET /api/members/{id}/picks` | `pick`, `game`, `team` | `game` also drives the kicked-off filter |
| `GET /api/leaderboard` | `app_user`, `pick`, `game` | One SQL statement — see below |
| `GET /api/teams` | `team` | |
| `GET /api/teams/{id}` | `team`, `athlete`, `coach_season`, `coach`, `game`, `pick`, `cfbd_sync` | **May call CFBD** — see §3 |
| `GET /api/athletes/{id}` | `athlete`, `team` | All seasons for the id, newest first |
| `GET /api/coaches/{id}` | `coach`, `coach_season` | |
| `GET /api/admin/users` | `app_user`, `pick` | Pick counts via one grouped query |
| `GET /api/admin/activity` | `pick_audit`, `app_user`, `game` | Names and game labels resolved in bulk |
| `GET /api/admin/quota` | `cfbd_call_log` | Counts the trailing 30 days. No CFBD call. |
| `GET /api/meta` | *(none)* | Config only — the one unauthenticated endpoint |

**The leaderboard is one statement**, in [LeaderboardService](backend/src/main/java/com/nickspicks/api/leaderboard/LeaderboardService.java).
It starts from `app_user` and `LEFT JOIN`s the pick aggregate, which is what makes a member with zero
picks a 0-0 row rather than an absent one. The optional week narrows the join, so both the record and
the pick count follow the dropdown.

### Writes

| Endpoint | Reads | Writes |
| --- | --- | --- |
| `PUT /api/me` | `app_user` (uniqueness check) | `app_user` |
| `POST /api/picks` | `game`, `weekly_entry` **(`SELECT … FOR UPDATE`)**, `pick` | `weekly_entry`, `pick`, `pick_audit` |
| `PUT /api/picks/{id}` | `pick`, `game`, `weekly_entry` **(locked)** | `pick`, `pick_audit` |
| `POST /api/picks/{id}/relock` | `pick`, `game`, `weekly_entry` **(locked)** | `pick`, `pick_audit` |
| `DELETE /api/picks/{id}` | `pick`, `game`, `weekly_entry` **(locked)** | `weekly_entry`, `pick_audit`, deletes `pick` |
| `PUT /api/admin/users/{id}/role` | `app_user` | `app_user` |
| `DELETE /api/admin/users/{id}` | `app_user` | Deletes `app_user` — cascades `pick`, `weekly_entry`, `pick_audit` |
| `POST /api/admin/ingest/*` | see §3 | see §3 |

Every pick mutation takes a **pessimistic write lock** on the member's `weekly_entry` row before
doing anything else. That is what serialises concurrent picks and makes the ten-pick cap hold; see
documentation.md §6 for why counting rows cannot.

The audit row is written **before** the delete on a cancel, so the row still has its final state to
record. `pick_audit` has no foreign key to `pick`, so the history survives.

---

## 3. CFBD → database

Base URL `https://api.collegefootballdata.com`, `Authorization: Bearer <key>`. Free tier is
**1,000 calls a month**, which is the constraint behind most of these choices.

All calls go through [CfbdClient](backend/src/main/java/com/nickspicks/api/cfbd/CfbdClient.java),
which logs every request to `cfbd_call_log` and refuses to call at all past 900 in the trailing 30
days — leaving headroom to diagnose an overrun rather than discovering it at zero.

| Endpoint | Query | Writes to | Frequency | Guard |
| --- | --- | --- | --- | --- |
| `/calendar` | `year` | `season_week` | 1 per season | `cfbd_sync('calendar', year)` |
| `/teams` | `year` | `team` | 1 per season | `cfbd_sync('teams', year)` |
| `/coaches` | `year` | `coach`, `coach_season` | 1 per season | `cfbd_sync('coaches', year)` |
| `/rankings` | `year` | `poll_ranking` | Weekly | — (upserts) |
| `/roster` | `team`, `year` | `athlete` | 1 per team, ever | `cfbd_sync('roster', teamId:year)` |
| `/games` | `year`, `seasonType=regular`, `classification=fbs` | `game` | Weekly, plus score polling | — |
| `/lines` | `year`, `week`, `seasonType=regular` | `game` (line columns) | Every 3h during the season | — |

**`/games` deliberately omits `week`.** One call returns all 888 games of the 2026 regular season
across 14 weeks; asking week by week costs a call each for exactly the same rows. That single change
turned ~14 calls into 1 and means every future week is loaded the moment the season is, so members
can look ahead without an admin remembering to fetch each week.

`/lines` still takes a week — that endpoint requires one, and only the near weeks have numbers posted
anyway.

### What each one supplies

**`/calendar`** — 16 rows for 2026. Gives `week`, `startDate`, `firstGameStart`, `lastGameStart`.
Populates `season_week`, which is what lets the week dropdown offer weeks nobody has ingested yet.
Without it, "available weeks" would be derived from `game` and you could never look ahead.

**`/teams`** — 684 programs across every division in a single call, the same cost as the FBS-only
endpoint. `app.cfbd.team-classifications` decides what is kept (default `[fbs, fcs]` = 266 rows).
Supplies school, mascot, abbreviation, conference, classification, colours, logo URLs, Twitter and
the venue block. Logos are picked from the `logos` array by light/dark variant.

**`/coaches`** — FBS only, and that is upstream, not a filter we apply: asking for an FCS school by
name returns `[]`. Each record carries a `seasons` array, which becomes `coach_season` rows and
drives the career table. Only the requested year's seasons come back, so careers are as deep as the
years you have ingested.

**`/roster`** — fetched lazily the first time anyone opens a team page, then never again. One call
per team for the life of the season, and only for teams somebody actually looks at. Athlete ids are
**strings**, and a player appears once per season, hence the `(id, season)` key. Works for FCS as
well as FBS.

> The feed can list the same athlete twice in one roster. The ingest de-duplicates by id, because
> the second occurrence would otherwise miss the not-yet-flushed first insert and collide on the
> primary key — taking the whole roster down with it. If a team is stuck with an empty roster from
> a failure like that, `POST /api/admin/ingest/roster?teamId=` clears the marker and refetches.

**`/rankings`** — every week and every poll for a season in one call. Only Playoff Committee, AP Top
25 and Coaches Poll are kept; the feed also carries FCS, D2 and D3 coaches polls. Rows carry
`teamId`, which is what lets a rank attach to a team without matching on name. Re-running upserts,
so this is the weekly button as new polls publish.

**`/games`** — used twice, for two different purposes:

- *Schedule sync* fills identity and timing: `homeId`/`awayId` (which is what makes team links work
  without a lookup), kickoff, `startTimeTBD`, venue, neutral site, pregame Elo.
- *Score sync* re-reads the same endpoint for `homePoints`/`awayPoints`/`completed`, then grades
  every newly-final game.

`completed` is a boolean rather than a status string, so `GameIngestService.applyScore` derives
`SCHEDULED` / `IN_PROGRESS` / `FINAL` from it plus the kickoff time.

**`/lines`** — returns a `lines` array per game, one entry per sportsbook. We take the first
available in preference order (DraftKings, Bovada, ESPN Bet, consensus) so members see one stable
number rather than a different book on each refresh. Supplies `spread`, `spreadOpen`, `overUnder`
and both moneylines. **Line movement updates `game` and never an existing `pick.locked_spread`.**

### Call budget

| Job | Schedule | Calls/month |
| --- | --- | --- |
| Schedule sync | Sunday 03:00 | ~5 |
| Line sync | Every 3h, Mon–Sat | ~150 |
| Score + grading | Every 15 min, **only while a game is live** | ~200 |
| Reference (calendar, teams, coaches) | Once per season | 3 |
| Rosters | On demand | ≤1 per team viewed |

The score poller runs a local query for a game with `kickoff <= now <= kickoff + 5h` **before**
calling out, so a Tuesday with no football costs nothing. Budget lands near 350/month.

Scheduled ingest is **off** in the local profile (`app.cfbd.enabled: false`) so a dev session cannot
quietly spend the month; use Admin → Data. It is on in production.

---

## Where the data comes from, per screen

A quick inverse view — if a screen is empty, this is what feeds it:

| Screen shows | Table | Loaded by |
| --- | --- | --- |
| Week dropdown | `season_week` | Admin → Data → *Calendar, teams & coaches* |
| Games and spreads | `game` | Admin → Data → *Schedule & lines* |
| Scores and results | `game`, `pick` | Admin → Data → *Scores & grading* |
| `#N` beside a team name | `poll_ranking` | Admin → Data → *Poll rankings* |
| Win probability | `game` | Admin → Data → *Scores & grading* (postgame only) |
| Team logos and links | `team` | Admin → Data → *Calendar, teams & coaches* |
| Rosters | `athlete` | Automatically, on first visit to that team |
| Coaches | `coach`, `coach_season` | Admin → Data → *Calendar, teams & coaches* (FBS only) |
| Leaderboard | `app_user`, `pick`, `game` | Members picking, then grading |
| Activity log | `pick_audit` | Members picking — starts from migration V3 |
