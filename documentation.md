# Nick's Picks — Documentation

A college football pick'em site. Members join a **group** — an isolated league with its own rules —
then see the week's games with point spreads, pick against the spread or the total, and are ranked
on that group's leaderboard. Every team, player and coach on the site is clickable through to its
own page.

| Part | Stack | Location |
| --- | --- | --- |
| Frontend | React 18 + Vite + Bootstrap 5, mobile-first | [frontend/](frontend/) |
| Backend | Spring Boot 3.5 + Maven, profile-driven config | [backend/](backend/) |
| Database | Supabase Postgres, schema owned by Flyway | Supabase project |
| Auth | Supabase Auth (ES256 JWT), verified by the API | — |
| Game data | CollegeFootballData API (free tier) | — |

```
Browser ──> React (Vite :5173 / static build in prod)
              │  fetch /api/**  with Authorization: Bearer <Supabase JWT>
              ▼
            Spring Boot API (:8080)
              │  verifies JWT against Supabase JWKS (no shared secret)
              │
              ├── JDBC (HikariCP) ──> Supabase Postgres
              └── HTTPS ────────────> api.collegefootballdata.com
```

The browser never talks to Postgres or to CFBD. It authenticates with Supabase, then calls the API,
which owns the database and every outbound data call.

> **[data-flow.md](data-flow.md)** is the companion reference: what every page calls, what every
> endpoint reads and writes, and which CFBD endpoint supplies each table.

---

## 1. The rules

| Rule | Where it is enforced |
| --- | --- |
| A **per-group cap** on picks per period, any mix of markets | `PickService` + the `cadence_entry` row lock |
| One pick per **market** per game **per group** | `unique (group_id, user_id, game_id, market)` |
| Create, edit or cancel until the **group's lock lead** before kickoff | [PickWindow.java](backend/src/main/java/com/nickspicks/api/pick/PickWindow.java) |
| Picks graded **against the spread** | [GradingService.java](backend/src/main/java/com/nickspicks/api/ingest/GradingService.java) |
| The line is **locked when the pick is made** | `pick.locked_line`, re-locked on edit |
| A pick against a **stale line is rejected** | `PickService.requireCurrentLine` → `409 LINE_MOVED` |
| Other members' picks are **hidden until kickoff** | `PickService.findRevealedForUserWeek`, server-side |
| Leaderboard ranks by **points, then wins, then fewest losses**, scored by the **group's** point values | `LeaderboardService` |
| Admin actions require the **ADMIN role** | `CurrentUserService.requireAdmin` → `403 FORBIDDEN` |
| Groups are **created by admins only** | `AdminGroupController` |
| Group authority is the **OWNER role**, and a group may have several | `GroupService.canManage` reads `group_member`, not `pick_group` |
| A group always keeps **at least one owner** | `GroupService.isLastOwner` blocks the last demotion or removal |

### Three markets

A pick plays the **spread** (`HOME` / `AWAY`), the **total** (`OVER` / `UNDER`) or the **winner**
(`HOME_WINNER` / `AWAY_WINNER`). All three grade
to the same WIN/LOSS/PUSH outcomes and draw on the same allowance, so a member spends their week
across whichever mix they like. What each is worth is the group's decision.

The market is **derived from the selection** in
[Selection.java](backend/src/main/java/com/nickspicks/api/pick/Selection.java) — each constant
carries its own — so an inconsistent pair cannot be built in code. The `pick.market` column exists
for the `(group_id, user_id, game_id, market)` unique key, with a check constraint pairing the two
as a backstop for anything writing to the table directly.

Totals grade against the combined score:

```
total = homeScore + awayScore
OVER  → WIN if total > lockedLine     UNDER → the reverse
```

Availability is per market — a game can carry a spread and no total — but the lock is shared,
because it is a property of kickoff rather than of a market. A group that has turned a market off
rejects picks in it outright, and the board does not draw its buttons.

**The winner market has no line**, which is what makes it more than a third enum value:

- `locked_line` is **null** for a winner pick, enforced by a check constraint that requires it for
  the other two. Storing a zero would be a lie in the audit trail, and the moneyline does not fit —
  those are integers well past `numeric(4,1)`, and most games carry none.
- Nothing can move, so there is no `LINE_MOVED` and no re-lock.
- Nothing needs posting, so a game is winner-pickable as soon as it is scheduled.
- A tie would push, which college football has not produced since overtime arrived in 1996. The
  branch is a guard against a bad score, not a case anyone will meet — so `winner_push_points` will
  realistically never pay out.

`HOME_WINNER` exists rather than reusing `HOME` because `Selection` derives its market from the
constant. A constant meaning two different things depending on a market passed beside it would give
up the guarantee that design exists for. Switching an existing pick from
one market to the other is rejected: it would change the row's identity under the unique key, so it
is a cancel and a new pick.

### Stale lines

The board a member is looking at can be minutes or hours old. Every pick therefore carries the
spread the page was showing; if it no longer matches, the pick is refused with `LINE_MOVED` and the
current number is returned so the card can refresh and the member can decide again. Comparison is
numeric, so `-7.5` and `-7.50` are the same line rather than a spurious conflict.

Without this, a tab left open overnight silently commits someone to whatever the number is now —
possibly several points from what they clicked.

### Taking a better line

When the spread moves in favour of the side a member already took, the card offers a re-lock. The
direction of "better" depends on the side, since spreads are from the home perspective: a HOME pick
improves as the spread **rises** (fewer points to give), an AWAY pick as it **falls** (more points
received). That sign logic lives only in `PickWindow.isLineImproved` and is covered by a test table —
getting it backwards would offer a button that quietly makes a pick worse.

Re-locking never changes sides, is refused unless the line genuinely improved, and still respects the
group's lock window.

### Roles

Every member is `MEMBER` until promoted. `app.admin-emails` promotes listed addresses on sign-in —
bootstrap only, so the first admin can exist; after that roles are managed from **Admin → Members**.
Admins cannot demote or delete themselves, which is what stops the site ending up with zero admins.

### The audit trail

`pick_audit` records every create, edit and cancel, with the previous selection and line on edits.
It is insert-only and has **no foreign key to `pick`**, so a cancelled pick keeps its history even
though the pick row is gone. Visible at **Admin → Activity log**.

Three things make a game unpickable: no posted line, a kickoff time still marked TBD, or the
group's lock window having closed.

### How grading works

Spreads are stored from the home team's perspective, matching CFBD — `-7.5` means the home team is
favored by 7.5. Add the spread to the home score and compare:

```
adjustedHome = homeScore + lockedSpread
margin = (pick is HOME) ? adjustedHome - awayScore
                        : awayScore - adjustedHome

margin > 0  WIN      margin < 0  LOSS      margin == 0  PUSH
```

A whole-number line can push; a half-point line never can. Picks on canceled games become `VOID` and
are excluded from the standings rather than counted as losses.

Worked example — home favored by 7.5, final score 24-20:

- Home won the game, but `24 + (-7.5) = 16.5 < 20`, so a **HOME pick loses** and an **AWAY pick
  wins**. Winning the game and covering the spread are different questions, which is the whole point
  of playing against the number.

### Why the line locks at pick time

Two members who both took the same side can legitimately get different results, because each is
graded on the number they actually saw. Later line movement updates the game but never touches an
existing `pick.locked_line`. Editing a pick re-locks the current line — you are committing to
today's number, not last week's.

---

## 2. Quick start

### Prerequisites

| Tool | Version here | Notes |
| --- | --- | --- |
| Java | 21 | |
| Maven | 3.9.9 | Not installed globally — use the bundled `mvnw` / `mvnw.cmd`. |
| Node.js | 22.x | |
| Docker | 29.x | Only needed to run the integration tests. |
| Supabase | — | Free project. |
| CFBD key | — | Free at [collegefootballdata.com/key](https://collegefootballdata.com/key). |

### Run it

```powershell
# backend - migrations run automatically on startup
cd backend
.\mvnw.cmd spring-boot:run

# frontend
cd frontend
npm install
npm run dev
```

Frontend at <http://localhost:5173>, API at <http://localhost:8080>. Vite proxies `/api` to the
backend, so there is no CORS in dev and no API URL to configure. `server.host: true` means the dev
site is reachable from a phone on the same wifi at `http://<your-lan-ip>:5173`.

### Load the data

A fresh database has no games. Sign in, then open **Data** in the nav (or call the endpoints
directly) and run, in order:

1. **Teams & coaches** — 2 API calls, once per season. Team pages do not work until this runs.
2. **This week's schedule & lines** — 2 API calls, weekly, and again to pick up line movement.
3. **Scores & grading** — 1 API call, after games finish.

```powershell
# equivalent from the command line, with a bearer token
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/ingest/reference
curl -X POST -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/admin/ingest/week?week=1"
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/ingest/scores
```

In production `app.cfbd.enabled=true` runs these on a schedule and you never touch them by hand.
Locally it is `false`, so a dev session cannot quietly burn the monthly quota.

---

## 3. Configuration and profiles

`application.yml` holds shared defaults; the active profile's file is layered on top.

| | `application.yml` | `application-local.yml` | `application-prod.yml` |
| --- | --- | --- | --- |
| Purpose | Shared defaults | Developer machine | Deployed |
| Credentials | — | in the file (gitignored) | env vars, **no defaults** |
| Scheduled ingest | `true` | `false` | `true` |
| `show-sql` | — | `true` | `false` |
| Log level | Spring default | `DEBUG` + SQL binds | `WARN` / `INFO` |
| Actuator | `health,info` | `+ env,configprops,metrics` | `health,info` |
| Health details | `never` | `always` | `never` |
| Hikari pool | — | 5 | `${DB_POOL_SIZE:10}` |
| CORS origins | `[]` | `localhost:5173` | `${ALLOWED_ORIGINS}` |
| Error responses | — | Spring default | message + stacktrace suppressed |

`spring.profiles.default: local`, so a bare `mvnw spring-boot:run` starts in local mode.

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
java -jar target\nickspicks-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Production references every secret as `${VAR}` with **no default**, so a misconfigured deploy fails
at startup instead of quietly connecting somewhere wrong. See §8 for how those get injected.

**`application-local.yml` is gitignored** because it contains a real database password.
[application-local.yml.example](backend/src/main/resources/application-local.yml.example) is the
committed template.

### Settings that shape behaviour

```yaml
app:
  pickem:
    season: 2026            # explicit, so the offseason needs no code change
    max-picks-per-week: 10
    lock-lead-minutes: 30
  cfbd:
    enabled: true           # scheduled ingest on/off
    api-key: ${CFB_DATA_API_KEY:}
    classification: fbs
```

`CurrentWeekResolver` picks the current week from kickoff times, not the calendar — college football
weeks are not seven days long.

---

## 4. Authentication

Supabase issues tokens; the API only verifies them. There is no signup endpoint here and no password
ever reaches this service.

```
Browser --(email+password)--> Supabase Auth --(ES256 JWT)--> Browser
Browser --(Bearer JWT)------> API --(fetch public key once)--> Supabase JWKS
```

Configured in [SecurityConfig.java](backend/src/main/java/com/nickspicks/api/security/SecurityConfig.java),
deliberately in code rather than YAML — it needs both a non-default algorithm and an audience check,
and splitting that across two files is how the halves drift apart.

Two details that are easy to get wrong:

- **`jwsAlgorithm(ES256)` is mandatory.** This project signs with an elliptic-curve key. Spring Boot
  defaults to RS256 and would reject every token with a confusing error.
- **The audience is checked.** Supabase puts `authenticated` in `aud` for signed-in users. Without
  that check, anon tokens would be accepted.

`CurrentUserService` maps the token's `sub` to a row in `app_user`, creating it on first sight — the
member row and the Supabase user cannot drift apart because there is only one write path.

Everything under `/api/**` requires a token except `/api/meta`. `/actuator/health` stays open for
your host's health checks.

---

## 5. Game data and the quota

The CFBD free tier is **1,000 calls a month**. That single number drove most of the data design.

### Ingested, not proxied

Detail pages are user-triggered. If a team page called the API on every view, a few members browsing
rosters would exhaust the month in an afternoon. So reference data lives in our own tables:

| Data | Cost | Strategy |
| --- | --- | --- |
| Teams (138 FBS) | 1 per season | Bulk. Team pages then cost **zero** |
| Coaches | 1 per season | Bulk |
| Rosters | 1 per team, ever | Fetched lazily the first time someone opens that team |
| Schedule | 1 per week | |
| Lines | every 3h | ~150/month |
| Scores | every 15 min **while a game is live** | ~200/month |

`cfbd_sync` records what has been fetched so a second page view never re-calls. `cfbd_call_log`
records every outbound call, which is what `/api/admin/quota` reports. `CfbdClient` refuses to call
at all past 900 calls in the trailing 30 days, leaving headroom to diagnose the overrun.

The score poller queries our own tables for a game plausibly underway before calling out, so a
Tuesday with no football costs **nothing**. Total budget lands near 350/month.

### Which programs are stored

`GET /teams` returns all 684 programs across every division in **one call** — the same cost as the
FBS-only endpoint — so `app.cfbd.team-classifications` decides what is kept. The default is
`[fbs, fcs]`, giving 266 teams for 2026. Add `ii` and `iii` to go further; it costs nothing extra.

Including FCS matters because FBS teams play non-FBS opponents constantly. With those teams stored,
an opponent like Southeast Missouri State is a real link with a logo, roster and schedule rather than
plain text.

`game.home_team_id` / `away_team_id` and `coach_season.team_id` still carry **no foreign key**
([V2](backend/src/main/resources/db/migration/V2__relax_team_references.sql)): the table is a cache,
not a complete dimension, and D2/D3 opponents remain outside it. `DtoMapper` resolves an unknown id
to `null` and `TeamLink` renders a plain name. This is not hypothetical — the FK version of the
schema failed on week 1 of 2026.

### Known limitations

**No FCS coaches — the data does not exist upstream.** `/coaches` returns FBS staff only. This is not
a filter the app applies: asking for a specific FCS school by name (`/coaches?year=2026&team=Montana`)
returns an empty array, verified against Montana, South Dakota State, Villanova, Jackson State and
Alabama State. There is deliberately no lazy per-team coach fetch, because it would spend one call per
FCS team page to retrieve nothing. FCS **rosters** are available and do load on demand, exactly like
FBS ones.

Note that classification follows the season: North Dakota State is FBS for 2026, so it does have coach
data.

**Coach career depth.** `/coaches?year=N` returns only that year's seasons, so a career table shows
the seasons you have ingested rather than a full history. Each extra year is one call.

---

## 6. Data model

Managed by Flyway ([db/migration/](backend/src/main/resources/db/migration/)); Hibernate runs with
`ddl-auto: validate` in every environment, so a drift between entity and table fails at startup.

```
app_user       id (= Supabase auth sub) · email · display_name · username (unique, the @handle)
team           id (CFBD) · school · mascot · conference · color · logo_url · venue_*
athlete        (id, season) · name · team_id · position · jersey · height · weight · hometown
coach          id · name · hire_date
coach_season   (coach_id, season, school) · team_id · games · wins · losses · sp_*
game           id (CFBD) · season · week · home/away team+id · kickoff · start_time_tbd
               home_spread · spread_open · over_under · moneylines · scores · elo · status
pick           id · group_id · user_id · game_id · selection · market · locked_line · result
               locked_line null for WINNER, required for SPREAD/TOTAL (check constraint)
               UNIQUE(group_id, user_id, game_id, market)
cadence_entry  (group_id, user_id, period_key) · pick_count
               period_key = '2026-W01' (weekly) or '2026-09-05' (daily)
pick_group     id · name · visibility · join_password · owner_id · group_type · cadence
               length_type · start_season · lock_lead_minutes · min/max_picks_per_cadence
               winner/spread/total_enabled · nine *_points columns · strikes_allowed
               team_pick_limit + scope
group_member   (group_id, user_id) · role (OWNER | MEMBER) · joined_at
group_join_request (group_id, user_id) unique · status (PENDING|APPROVED|DENIED)
               requested_at · decided_at · decided_by
cfbd_sync      (resource, sync_key)      what has been fetched
cfbd_call_log  endpoint · status · called_at   quota accounting
```

### Groups

A group is an isolated picking league with its own rules. Settings are typed columns rather than a
JSON blob, so Postgres rejects a nonsensical combination even if something writes to the table
directly; `GroupSettings.validate()` duplicates the cross-field rules in Java only to produce a
readable message. The table is `pick_group` because `group` is a SQL reserved word and
`LeaderboardService` reads the schema through raw SQL.

The one combination worth knowing: **elimination groups must be `PER_YEAR`**. A continuous
elimination pool ends the first time everyone is out and can never start over. The **group type is
fixed after creation** — the two types score and cap differently, so switching an established league
would re-interpret picks made under the other rules.

### Owners, creators and joining

Authority is the `OWNER` role in `group_member`, which several members can hold at once. It is
deliberately *not* `pick_group.created_by`: that column records only who made the group, drives the
"creator" badge, and is nullable so a group outlives its creator's account. The last owner cannot be
demoted or removed — a group with no owner has nobody who can configure it, approve anyone or delete
it.

`require_approval` turns joining into a request. Every route in — search, and later a shared link —
produces the same pending row, so the setting means what it says however someone arrived. The
password is checked *before* the request is queued, so a wrong one is refused immediately rather
than wasting an owner's time. One row per person per group, reused on a re-request, so an owner sees
a queue rather than a log of attempts.

**Wired up as of V14**: picks, the games board and the leaderboard are all group-scoped, and the
group's own cap, lock lead, enabled markets and point values decide what happens.

> **Still configurable but not yet enforced**: elimination strikes, the minimum picks per cadence,
> and the per-team pick limit. Those settings save and display, but nothing acts on them yet.

### Why `cadence_entry` exists

The pick cap cannot be enforced by counting picks. Two concurrent requests both read nine and
both insert, and the member ends up with eleven. `SELECT ... FOR UPDATE` does not help either,
because the conflicting row does not exist yet — there is nothing to lock.

So every pick mutation takes a pessimistic write lock on the member's `cadence_entry` row first,
which serialises them. `PickRulesIntegrationTest.twoConcurrentTenthPicksLeaveExactlyTenPicks` fires
six threads at the tenth slot and asserts exactly ten picks land.

It replaced `weekly_entry` in V14. That table's key hard-coded the week as the counting period,
which a daily group cannot use; the key is now an opaque `period_key` that
[CadencePeriod](backend/src/main/java/com/nickspicks/api/pick/CadencePeriod.java) derives from the
group's cadence and the *game's* kickoff — not the clock, so a pick made on Tuesday for Saturday
counts against Saturday. Daily buckets use the America/New_York date, so a late Eastern kickoff
stays on its own game day instead of rolling into the next one.

The old `CHECK (pick_count BETWEEN 0 AND 10)` is gone with it: the cap is per group now and may be
absent entirely, so the upper bound cannot live in the schema.

### Ranking

Lives only in `LeaderboardService` — V12 dropped the competing `v_standings` view precisely because
the two had diverged. Ties break on points, then most wins, then fewest losses, then name.

Scoring is per group. The SQL returns a win/loss/push count *per market* and Java multiplies those
by the group's nine `*_points` columns, so a league can pay 2 for a spread win and 1 for a total,
or −1 for a loss. The arithmetic is in Java rather than the ORDER BY because the group's numbers
are parameters, not constants, and one copy of the rule is easier to trust than two.

The board starts from `group_member`, not `app_user`, so it contains the league and nobody else.

Note the trade-off the cap creates: where a group allows "up to ten", someone who picks all ten
every week at 60% outranks someone who picks five carefully at 75%.

### RLS

Enabled with no policies on every table. The API connects as `postgres` and bypasses RLS, so this
only ensures the public anon key can read nothing if it leaks.

---

## 7. API

All paths need a bearer token except `/api/meta`. Errors are RFC 7807 `ProblemDetail` with a
machine-readable `code`, so the UI can tell "too late" from "already have ten" without matching prose.

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/me` | Caller's profile; provisions the member row |
| `GET` | `/api/weeks/current` | Season, current week, available weeks |
| `GET` | `/api/games?groupId&season&week` | Games with spread, `locked`, `locksAt`, caller's pick, team summaries |
| `GET` | `/api/games/{id}?groupId` | Adds opening line, O/U, moneylines, Elo, and member picks once kicked off |
| `GET` | `/api/games/filters?season&week` | Conferences, teams and widest line that week |
| `GET` | `/api/picks?groupId&season&week` | Caller's picks + `picksUsed` / `picksRemaining` (null when the group sets no cap) |
| `POST` | `/api/picks?groupId` | `{gameId, selection, expectedLine}` → 201 |
| `PUT` | `/api/picks/{id}?groupId` | `{selection, expectedLine}` — re-locks the current line |
| `POST` | `/api/picks/{id}/relock?groupId` | Move onto a better line, same side |
| `DELETE` | `/api/picks/{id}?groupId` | Returns the updated card, and frees the slot |
| `GET` | `/api/members/{id}/picks?groupId` | Filtered server-side to games already kicked off |
| `GET` | `/api/leaderboard?groupId&season&week` | The group's members; no `week` means the whole season. A continuous group with no `season` is all-time |
| `PUT` | `/api/me` | `{displayName}` — unique, case-insensitive |
| `GET` | `/api/teams`, `/api/teams/{id}?groupId` | Detail includes roster, staff and schedule. `groupId` is optional — without it the schedule renders unmarked |
| `GET` | `/api/athletes/{id}`, `/api/coaches/{id}` | |
| `GET` | `/api/groups/mine` | Groups the caller belongs to |
| `GET` | `/api/groups/search?q` | Public groups only; `passwordRequired`, never the password |
| `GET` | `/api/groups/{id}` | Full settings — members and admins only |
| `GET` | `/api/groups/{id}/members` | Roster, owner first |
| `POST` | `/api/groups/{id}/join` | `{password}`; public groups only. Returns `{pending, group}` — `pending` when the group vets joiners |
| `GET` | `/api/groups/{id}/requests` | Owners — who is waiting to be let in |
| `POST` | `/api/groups/{id}/requests/{userId}/approve` \| `/deny` | Owners — 204 |
| `PUT` | `/api/groups/{id}/members/{userId}/role` | Owners — `{role}`; refuses to demote the last owner |
| `PUT` | `/api/groups/{id}` | Owner (or admin) edits settings |
| `DELETE` | `/api/groups/{id}` | Owner (or admin); 204, takes membership with it |
| `DELETE` | `/api/groups/{id}/members/{userId}` | Owner removes, or a member leaves; the owner row is refused |
| `GET`&nbsp;/&nbsp;`POST` | `/api/admin/groups` | **admin** — list all, or create (creator becomes owner) |
| `GET`/`PUT`/`DELETE` | `/api/admin/groups/{id}` | **admin** — any group |
| `GET`&nbsp;/&nbsp;`POST` | `/api/admin/groups/{id}/members` | **admin** — roster, or add `{userId}` with no password |
| `DELETE` | `/api/admin/groups/{id}/members/{userId}` | **admin** |
| `GET` | `/api/admin/users` | **admin** — with pick counts |
| `PUT` | `/api/admin/users/{id}/role` | **admin** — `{role}` |
| `DELETE` | `/api/admin/users/{id}` | **admin** — cascades picks and history |
| `GET` | `/api/admin/activity?userId&limit` | **admin** — the pick audit feed |
| `POST` | `/api/admin/ingest/{reference,week,scores}` | **admin** — spends quota |
| `GET` | `/api/admin/quota` | **admin** — no API call |

| `code` | Status | Meaning |
| --- | --- | --- |
| `PICK_WINDOW_CLOSED` | 409 | Inside 30 minutes of kickoff |
| `WEEKLY_LIMIT_REACHED` | 409 | Already at the group's cap for the period |
| `LINE_MOVED` | 409 | Stale page; carries `currentSpread` |
| `INVALID_PICK` | 409 | Duplicate pick, or a re-lock that would not improve the line |
| `USERNAME_TAKEN` | 409 | Another member has that name |
| `INVALID_GROUP_SETTINGS` | 400 | Settings that are individually valid but contradict each other |
| `GROUP_PASSWORD_REQUIRED` | 400 | The group is locked and no password was sent |
| `GROUP_PASSWORD_INCORRECT` | 403 | Wrong password |
| `ALREADY_A_MEMBER` | 409 | Already in that group |
| `FORBIDDEN` | 403 | Signed in, but not an admin — or not the group's owner |
| `UPSTREAM_UNAVAILABLE` | 503 | CFBD unreachable or out of quota |
| `VALIDATION_FAILED` | 400 | Body invalid; `errors` maps field → message |
| `NOT_FOUND` | 404 | |

**Admin endpoints are open to any signed-in member.** Deliberate for a private site among friends,
and the obvious place to add a role check if membership grows — each call spends real quota.

---

## 8. Frontend

```
src/
├── lib/supabase.js        client, publishable key only
├── auth/                  AuthProvider, ProtectedRoute
├── api/client.js          attaches the bearer token, unwraps ProblemDetail
├── components/
│   ├── links.jsx          TeamLink · AthleteLink · CoachLink · TeamLogo
│   ├── common.jsx         LockCountdown · ResultBadge · formatSpread · formatKickoff
│   ├── GameCard.jsx       one game, two sides as pick buttons
│   ├── GroupSettingsForm.jsx  every group setting, tabbed; controlled by its parent
│   └── WeekSelector.jsx
└── pages/                 Login · Games · GameDetail · MyPicks · Leaderboard
                           Groups · GroupDetail · Team · Athlete · Coach · Admin
```

### Everything is clickable

Teams, athletes and coaches always render through `TeamLink` / `AthleteLink` / `CoachLink`, so a name
is a link in one place and a link everywhere. If you are writing a bare team name in JSX, use
`TeamLink` instead.

The API supports this by embedding team summaries — id, logo, colour — inside games, rosters and
schedules, so the frontend never has to look one up to draw a link. Where the record is missing (a
non-FBS opponent), the components degrade to plain text rather than a dead link.

| Route | Page |
| --- | --- |
| `/login` | Email + password, themed to match the site |
| `/` | The games board: pick buttons, lock countdowns, filters, a sticky pick counter |
| `/?mine=1` | The same board filtered to your picks — this replaced the separate My Picks page |
| `/games/:id` | Line detail, and everyone's picks once it kicks off |
| `/leaderboard` | Every member, week dropdown, name search, click through to a card |
| `/members/:id` | Another member's locked picks |
| `/groups` | Your groups, plus search-and-join for public ones |
| `/groups/:id` | A group's settings and roster; editable by the owner, read-only for members |
| `/profile` | Change your display name and username |
| `/teams/:id` | Schedule and roster tabs, staff, colours |
| `/athletes/:id`, `/coaches/:id` | Player bio; coach career table |
| `/admin/members` | **admin** — roles and account removal |
| `/admin/groups` | **admin** — every group, and the create form |
| `/admin/groups/:id` | **admin** — settings and membership for any group |
| `/admin/data` | **admin** — ingest triggers with a quota meter |
| `/admin/activity` | **admin** — the pick log |

### Why My Picks is a filter, not a page

Managing a pick and making one are the same task on the same object. Splitting them meant the line
you picked at lived on one page and the line you would pick at lived on another, so noticing a
favourable move required visiting both. The game card now carries the comparison and the re-lock,
and `/my-picks` redirects to `/?mine=1`.

### Mobile

Most traffic is expected on phones:

- `viewport-fit=cover` plus `env(safe-area-inset-bottom)` — content clears the home indicator.
- Form controls forced to 16px under 576px — stops iOS Safari zooming on focus.
- Navbar collapses on route change, so the burger menu never covers the new page.
- Game cards one-up on phones; week chips scroll sideways rather than wrapping into a tall stack.
- Hover lift is inside `@media (hover: hover)` so it never sticks on touch.
- `100dvh` shell — correct with browser chrome shown or hidden.
- Larger button and input padding via Sass variables, giving ~44px touch targets.

The lock countdown and disabled buttons are **convenience only**. The server decides; a tab left open
past the window gets a 409 and the page refreshes itself.

### Bootstrap theming

[theme.scss](frontend/src/styles/theme.scss) overrides Bootstrap's Sass variables *before* importing
Bootstrap, so the framework recompiles with those values rather than being patched afterwards. Change
`$primary` and buttons, links, badges and focus rings all follow.

### Environment

Vite reads `.env` / `.env.local`, **never `.env.example`**:

```
VITE_API_BASE_URL=                # empty in dev; the proxy handles it
VITE_SUPABASE_URL=https://<ref>.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

`VITE_*` values are inlined into the bundle and are therefore public. The publishable/anon key is
designed for that. The service key must never appear here.

---

## 9. Deploying

### Backend

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target\nickspicks-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

| Variable | Example |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SUPABASE_DB_URL` | `jdbc:postgresql://<host>:6543/postgres?sslmode=require&gssEncMode=disable` (transaction pooler) |
| `SUPABASE_MIGRATION_URL` | same host on **5432** (session pooler) — see below |
| `SUPABASE_DB_USER` / `SUPABASE_DB_PASSWORD` | *(secret)* |
| `SUPABASE_URL` | `https://<ref>.supabase.co` — also the JWKS source |
| `CFB_DATA_API_KEY` | *(secret)* |
| `ALLOWED_ORIGINS` | `https://nickspicks.example.com` (comma-separated for several) |
| `PORT` | injected by most hosts; defaults to 8080 |

Secrets arrive as environment variables from the platform's secret store — Docker `-e`, a PaaS
secrets panel, a Kubernetes `Secret` via `secretKeyRef`, or `EnvironmentFile=` under systemd. Never
`ENV` in a Dockerfile (it bakes into the image layer) and never echoed into a file during a build
(it lands in the build log).

**Two Supabase ports, on purpose.** The app pool uses the transaction pooler on 6543 with
`prepareThreshold=0`, because that pooler does not support server-side prepared statements. Flyway
uses the session pooler on 5432, because it takes a session-level advisory lock that PgBouncer's
transaction mode does not hold reliably.

### Frontend

```powershell
cd frontend
npm run build      # -> dist/
```

Static output. The host must do two things: rewrite unknown paths to `/index.html` (otherwise
`/leaderboard` 404s on a hard refresh), and set `VITE_API_BASE_URL` at build time — then add that
origin to the backend's `ALLOWED_ORIGINS`.

---

## 10. Testing

```powershell
cd backend
.\mvnw.cmd test
```

**41 tests: 39 run, 2 skipped** (the opt-in live test below).

| Suite | Covers |
| --- | --- |
| `GradingServiceTest` | 18-case truth table: favorite covers/fails/pushes, underdog, half-point lines, pick'em, blowouts |
| `PickWindowTest` | The lock boundary at 29:59 / 30:00 / 30:01, no line, TBD kickoff, in-progress, reveal timing, **line-improvement direction for both sides** |
| `PickRulesIntegrationTest` | The cap, **six concurrent tenth picks**, the window on create/edit/cancel, slot refund, re-locking, line movement isolation, pick hiding, **stale-line rejection** |
| `PickApiIntegrationTest` | 401s, public `/api/meta`, member provisioning, HTTP contract, `code` values, `locked` flags |
| `LeaderboardIntegrationTest` | Grade → standings → ranking, and voiding canceled games |
| `LeaderboardApiIntegrationTest` | Members with no picks still listed; the week filter narrows record and count |
| `AdminAndAuditIntegrationTest` | Members get 403 on admin routes, bootstrap promotion, no self-demotion, **full create → update → cancel audit trail** |

Integration tests run against a **Postgres container**, not H2 — the migrations use Postgres syntax
(aggregate `filter`, `gen_random_uuid()`, row level security) that H2 cannot execute, and testing
against a database that accepts different DDL proves very little. Requires Docker.

> **Docker Engine 29 note.** It refuses Engine API versions below 1.44, which is lower than the
> docker-java client Testcontainers bundles negotiates by default — every container request comes
> back `400` with an empty body. The pom sets `-Dapi.version=1.44` for surefire, so `mvnw test` just
> works.

### Live smoke test

Opt-in, because it spends ~6 calls of the monthly allowance:

```powershell
.\mvnw.cmd test "-Dtest=CfbdLiveIngestTest" "-Dcfbd.live=true" "-Dcfbd.key=YOUR_KEY"
```

Worth running after touching the ingest mapping — a renamed upstream field fails silently otherwise,
leaving nulls in the database rather than throwing. This test is what caught the foreign-key problem
described in §5.

---

## 11. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `mvn` not recognised | Use `.\mvnw.cmd` from `backend/`. |
| Every request 401 | Frontend has no Supabase keys — check `.env.local` exists (not just `.env.example`) and restart Vite. |
| 401 with a valid-looking token | `jwsAlgorithm` mismatch. This project is ES256; Spring's default is RS256. |
| `Found non-empty schema(s) "public" but no schema history table` | Adopting Flyway on an existing DB. `baseline-on-migrate: true` is already set. |
| `prepared statement "S_1" already exists` | Transaction pooler without `prepareThreshold=0`. |
| Flyway hangs or lock errors | It is pointed at 6543. Use `SUPABASE_MIGRATION_URL` on 5432. |
| `SSL error: Remote host terminated the handshake` (works locally, fails on a host like Render) | pgjdbc's GSS encryption negotiation step confuses Supabase's pooler in some container environments. Add `&gssEncMode=disable` to every `jdbc:postgresql://...` URL. |
| `violates foreign key constraint "game_..._team_id_fkey"` | A pre-V2 database. Run migrations. |
| Games list is empty | Nothing ingested yet — run the Data page steps in order. |
| Team page has no roster | Roster fetch failed or quota is exhausted. Check `/api/admin/quota`. |
| `UPSTREAM_UNAVAILABLE` | No CFBD key, or the 900-call safety ceiling was hit. |
| Testcontainers `400` / "no valid configuration" | See the Docker Engine 29 note in §10. |
| Frontend loads, every API call 404s | `VITE_API_BASE_URL` wrong, or the host lacks an SPA fallback. |

---

## 12. Status

| Check | Result |
| --- | --- |
| `mvnw test` | 235 passing, 10 skipped (opt-in live tests) |
| Over/under picks | Verified live — spread and total on the same game, shared cap, per-market stale-line and duplicate rejection |
| `npm run build` | Passing — 429 modules |
| Flyway V1–V12 on Supabase | Applied; `ddl-auto: validate` passes |
| Flyway V13 (groups) | Applied to Supabase; first group created through the admin screen |
| Flyway V14 (group-scoped picks) | Applied to Supabase |
| Flyway V15 (co-owners, join approval) | Applied to Supabase |
| Flyway V16 (group favourites) | Applied to Supabase |
| Flyway V17 (winner market) | Applied to Supabase |
| Flyway V18 (display name + username) | Applied to Supabase; every existing handle kept its value |
| Flyway V19 (per-market pick limits) | Applied to Supabase; additive, all six columns null = no limit |
| Flyway V20 (cadence settlement) | Applied to Supabase; two new tables, nothing altered |
| Flyway V21 (group sharing) | Applied to Supabase; two new tables plus one defaulted column |
| Groups | Creation, settings, search, join, membership and delete — verified live |
| Group-scoped picks and leaderboard | Covered by tests; not yet exercised against the live database |
| Supabase Auth end to end | Verified — signup, ES256 token, member provisioning, role on `/api/me` |
| CFBD ingest | Verified live — 16 calendar weeks, 266 teams (FBS + FCS), 138 coaches, 99 week-1 games with lines |
| Week look-ahead | Verified live — 15 weeks selectable, 1 loaded |
| Pick rules | Verified live — 10 accepted, 11th `409 WEEKLY_LIMIT_REACHED` |
| Stale-line guard | Verified live — wrong `expectedLine` → `409 LINE_MOVED` with the real number |
| Audit trail | Verified live — CREATE → UPDATE (with previous values) → CANCEL survives the cancel |
| Leaderboard | Verified live — lists members with zero picks; week filter narrows record and count |
| Detail pages | Verified live — game, team (116-man roster), athlete, coach |
| Grading → leaderboard | Verified by integration test, **not yet against a real finished game** |

> Picks made before V3 have no audit rows — the trail starts when the table does.
