package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdQuotaService;
import com.nickspicks.api.cfbd.CfbdQuotaSnapshot;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRepository;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manual ingest triggers. Admin role required - each call spends real quota.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminIngestController {

    private final GameIngestService gameIngest;
    private final ReferenceIngestService referenceIngest;
    private final CurrentWeekResolver weeks;
    private final CfbdClient cfbd;
    private final CfbdQuotaService quotaService;
    private final CurrentUserService currentUser;
    private final TeamRepository teams;
    private final GameRepository games;
    private final GradingService grading;

    public AdminIngestController(GameIngestService gameIngest,
                                 ReferenceIngestService referenceIngest,
                                 CurrentWeekResolver weeks, CfbdClient cfbd,
                                 CfbdQuotaService quotaService,
                                 CurrentUserService currentUser, TeamRepository teams,
                                 GameRepository games, GradingService grading) {
        this.teams = teams;
        this.games = games;
        this.grading = grading;
        this.gameIngest = gameIngest;
        this.referenceIngest = referenceIngest;
        this.weeks = weeks;
        this.cfbd = cfbd;
        this.quotaService = quotaService;
        this.currentUser = currentUser;
    }

    /**
     * Calendar, teams, coaches and season records for a season. One API call
     * per part.
     *
     * <p>{@code parts} names which to run, so a season whose coaches are not
     * published yet can have its calendar refreshed without spending a call to
     * find that out again. Omitted means all four.
     */
    @PostMapping("/ingest/reference")
    public Map<String, Object> ingestReference(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) Integer season,
                                               @RequestParam(required = false) List<String> parts) {
        currentUser.requireAdmin(jwt);
        int year = season == null ? weeks.currentSeason() : season;
        Set<String> wanted = parts == null || parts.isEmpty()
                ? Set.of("calendar", "teams", "coaches", "records")
                : parts.stream().map(part -> part.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("season", year);
        if (wanted.contains("calendar")) {
            result.put("calendarWeeks", referenceIngest.ingestCalendar(year));
        }
        if (wanted.contains("teams")) {
            result.put("teams", referenceIngest.ingestTeams(year));
        }
        if (wanted.contains("coaches")) {
            result.put("coaches", referenceIngest.ingestCoaches(year));
        }
        if (wanted.contains("records")) {
            result.put("records", referenceIngest.ingestRecords(year));
        }
        result.put("callsUsedLast30Days", cfbd.callsThisMonth());
        return result;
    }

    /**
     * The whole season's schedule and every posted line. Two API calls.
     *
     * <p>Both are season-wide: /games and /lines each charge one call whether
     * you ask for a single week or the lot, so there is nothing to gain by
     * narrowing and a call per week to lose.
     */
    @PostMapping("/ingest/games")
    public Map<String, Object> ingestGames(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) Integer season) {
        currentUser.requireAdmin(jwt);
        int year = season == null ? weeks.currentSeason() : season;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("season", year);
        result.put("games", gameIngest.ingestSchedule(year));
        result.put("linesUpdated", gameIngest.ingestLines(year));
        result.put("callsUsedLast30Days", cfbd.callsThisMonth());
        return result;
    }

    /** Scores + grading for the season. One API call. */
    @PostMapping("/ingest/scores")
    public Map<String, Object> ingestScores(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) Integer season) {
        currentUser.requireAdmin(jwt);
        int year = season == null ? weeks.currentSeason() : season;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("season", year);
        result.put("gamesGraded", gameIngest.ingestScores(year));
        result.put("callsUsedLast30Days", cfbd.callsThisMonth());
        return result;
    }

    /**
     * Poll rankings for the whole season. One API call - /rankings returns
     * every week and every poll at once, so this is the button to press each
     * week as new polls publish.
     */
    @PostMapping("/ingest/rankings")
    public Map<String, Object> ingestRankings(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Integer season) {
        currentUser.requireAdmin(jwt);
        int year = season == null ? weeks.currentSeason() : season;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("season", year);
        result.put("rankingRows", referenceIngest.ingestRankings(year));
        result.put("callsUsedLast30Days", cfbd.callsThisMonth());
        return result;
    }

    /**
     * Re-fetch one team's roster, ignoring the "already asked" marker. One
     * API call. For a team whose first fetch failed, or whose data the
     * provider has since corrected.
     */
    @PostMapping("/ingest/roster")
    public Map<String, Object> ingestRoster(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam int teamId,
                                            @RequestParam(required = false) Integer season) {
        currentUser.requireAdmin(jwt);
        int year = season == null ? weeks.currentSeason() : season;

        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team %d not found".formatted(teamId)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("season", year);
        result.put("team", team.getSchool());
        result.put("players", referenceIngest.refreshRoster(team, year));
        result.put("callsUsedLast30Days", cfbd.callsThisMonth());
        return result;
    }

    /**
     * The account's real quota state, from CFBD's own {@code /info}. Refreshed
     * at most once a day - see {@code CfbdQuotaService} - so most visits to
     * this page cost nothing.
     */
    @GetMapping("/quota")
    public Map<String, Object> quota(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", cfbd.isConfigured());

        if (cfbd.isConfigured()) {
            CfbdQuotaSnapshot snapshot = quotaService.current();
            if (snapshot != null) {
                result.put("usedCalls", snapshot.getUsedCalls());
                result.put("remainingCalls", snapshot.getRemainingCalls());
                result.put("monthlyLimit", snapshot.getMonthlyLimit());
                result.put("resetAt", snapshot.getResetAt());
                result.put("fetchedAt", snapshot.getFetchedAt());
            }
        }
        return result;
    }

    /**
     * Force-regrades every pick on a game against its currently stored
     * score - including ones already settled. This is the one grading path
     * in the app that is not idempotent-on-PENDING: it exists specifically
     * to correct a pick the ESPN minute-by-minute poller ({@code
     * EspnScoreIngestService}, an unofficial data source) may have settled
     * incorrectly, since {@link GradingService#gradeGame} used everywhere
     * else can never touch an already-graded pick.
     *
     * <p>{@code gameId} is CFBD's id, which is also ESPN's own event id -
     * the same id space this whole app already keys games by.
     */
    @PostMapping("/games/{gameId}/regrade")
    public Map<String, Object> regradeGame(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable long gameId) {
        currentUser.requireAdmin(jwt);

        Game game = games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));

        if (game.getStatus() != GameStatus.FINAL && game.getStatus() != GameStatus.CANCELED) {
            throw new GameNotGradableException(
                    "Game %d is not final or canceled yet".formatted(gameId));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gameId", gameId);
        result.put("picksRegraded", grading.regradeGame(game));
        return result;
    }
}
