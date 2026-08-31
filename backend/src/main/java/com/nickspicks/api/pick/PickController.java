package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupService;
import com.nickspicks.api.ranking.RankingService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRepository;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PickController {

    private final PickService picks;
    private final GameRepository games;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;
    private final AppProperties properties;
    private final PickWindow window;
    private final TeamRepository teams;
    private final RankingService rankings;
    private final GroupService groups;

    public PickController(PickService picks, GameRepository games, CurrentWeekResolver weeks,
                          CurrentUserService currentUser, DtoMapper mapper,
                          AppProperties properties, PickWindow window, TeamRepository teams,
                          RankingService rankings, GroupService groups) {
        this.picks = picks;
        this.games = games;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.properties = properties;
        this.window = window;
        this.teams = teams;
        this.rankings = rankings;
        this.groups = groups;
    }

    /**
     * Builds the game card for whichever game a pick action just touched, so
     * create/update/relock/delete can hand it straight back instead of the
     * caller making a second request. Deliberately cheaper than {@code
     * GameService.detail()}: no CFBD ATS refresh, no ESPN box score, no
     * full-table user/team scans - none of that is needed to show an updated
     * pick on the games board, and paying for it on every click is what made
     * pick actions feel slow.
     */
    private ApiDtos.GameSummary gameSummaryFor(Group group, UUID userId, Game game) {
        List<Pick> myPicks = picks.findForUserGame(group.getId(), userId, game.getId());
        Map<Integer, Integer> ranks = rankings.rankLookup(game.getSeason(), game.getWeek());
        return mapper.gameSummary(game, myPicks, null, ranks, group.getLockLeadMinutes());
    }

    private Game requireGame(Long gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));
    }

    /**
     * @param date one day, for a group that picks daily. A week holds seven of
     *             those groups' allowances, so asking by week could only ever
     *             return a number that contradicts what picking enforces -
     *             which is why the board sends the day it is actually showing.
     */
    @GetMapping("/picks")
    public ApiDtos.WeekPicks myPicks(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam UUID groupId,
                                     @RequestParam(required = false) Integer season,
                                     @RequestParam(required = false) Integer week,
                                     @RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);
        int resolvedSeason = season == null ? weeks.currentSeason() : season;
        int resolvedWeek = week == null ? weeks.currentWeek() : week;

        boolean byDay = date != null && group.getCadence() == Cadence.DAILY;

        List<Pick> held = byDay
                ? picks.findForUserDay(group.getId(), userId, date)
                : picks.findForUserWeek(group.getId(), userId, resolvedSeason, resolvedWeek);

        List<ApiDtos.PickWithGame> rows = withGames(group, held);

        // Counted from the picks themselves, which is true for either cadence -
        // the counter row is keyed by period, and a daily group has several
        // periods inside one week.
        int used = rows.size();

        Integer max = group.getMaxPicksPerCadence();
        // With a day named, a daily group has a real countdown for the first
        // time. Without one, null still means "no single number describes this".
        Integer remaining = byDay
                ? (max == null ? null : Math.max(0, max - used))
                : picks.remainingPicks(group, userId, resolvedSeason, resolvedWeek);

        return new ApiDtos.WeekPicks(resolvedSeason, resolvedWeek, used, remaining, max,
                group.getCadence(), group.getMinPicksPerCadence(),
                picks.marketBudgets(group, held), rows);
    }

    @PostMapping("/picks")
    public ResponseEntity<ApiDtos.PickResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID groupId,
            @Valid @RequestBody ApiDtos.CreatePickRequest request) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);

        Pick pick = picks.create(group, userId, request.gameId(), request.selection(),
                request.expectedLine());
        Game game = requireGame(pick.getGameId());
        return ResponseEntity.created(URI.create("/api/picks/" + pick.getId()))
                .body(new ApiDtos.PickResponse(mapper.pickSummary(pick),
                        gameSummaryFor(group, userId, game)));
    }

    @PutMapping("/picks/{id}")
    public ApiDtos.PickResponse update(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID id,
                                       @RequestParam UUID groupId,
                                       @Valid @RequestBody ApiDtos.UpdatePickRequest request) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);

        Pick pick = picks.update(group, userId, id, request.selection(), request.expectedLine());
        Game game = requireGame(pick.getGameId());
        return new ApiDtos.PickResponse(mapper.pickSummary(pick),
                gameSummaryFor(group, userId, game));
    }

    /**
     * Re-lock onto the game's current line, keeping the same side. Rejected
     * unless the line actually improved.
     */
    @PostMapping("/picks/{id}/relock")
    public ApiDtos.PickResponse relock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @RequestParam UUID groupId) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);

        Pick pick = picks.relock(group, userId, id);
        Game game = requireGame(pick.getGameId());
        return new ApiDtos.PickResponse(mapper.pickSummary(pick),
                gameSummaryFor(group, userId, game));
    }

    /** Returns the game's updated card state rather than 204 - there is no pick left to report. */
    @DeleteMapping("/picks/{id}")
    public ApiDtos.GameSummary delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @RequestParam UUID groupId) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);
        Game game = picks.delete(group, userId, id);
        return gameSummaryFor(group, userId, game);
    }

    /**
     * Another member's card. Games that have not kicked off are filtered out
     * server-side.
     */
    @GetMapping("/members/{userId}/picks")
    public List<ApiDtos.PickWithGame> memberPicks(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID userId,
                                                  @RequestParam UUID groupId,
                                                  @RequestParam(required = false) Integer season,
                                                  @RequestParam(required = false) Integer week) {
        // The caller has to be in the group to read anyone's card in it.
        Group group = groups.requirePlayable(groupId, currentUser.resolveId(jwt));

        int resolvedSeason = season == null ? weeks.currentSeason() : season;

        // No week means the whole season, matching the leaderboard's
        // "Overall". Deliberately not defaulting to the current week: that
        // moves on the moment a slate finishes, so a member with a full season
        // of picks would show an empty card the day after the last game.
        return withGames(group,
                picks.findRevealedForUser(group.getId(), userId, resolvedSeason, week));
    }

    /**
     * Attaches each pick's game.
     *
     * <p>Games and teams are fetched once for the whole list rather than per
     * pick - ten picks would otherwise cost ten game lookups plus twenty team
     * lookups to render one page.
     */
    private List<ApiDtos.PickWithGame> withGames(Group group, List<Pick> picksToMap) {
        if (picksToMap.isEmpty()) {
            return List.of();
        }

        Map<Long, Game> gameCache = games
                .findAllById(picksToMap.stream().map(Pick::getGameId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));

        Map<Integer, Team> teamCache = teams.findAll().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity(), (a, b) -> a));

        return picksToMap.stream()
                .map(pick -> {
                    Game game = gameCache.get(pick.getGameId());
                    return new ApiDtos.PickWithGame(
                            mapper.pickSummary(pick),
                            game == null
                                    ? null
                                    : mapper.gameSummary(game, List.of(pick), teamCache,
                                            group.getLockLeadMinutes()),
                            game != null
                                    && window.isOpen(game, group.getLockLeadMinutes())
                                    && window.isLineImproved(pick, game));
                })
                .toList();
    }
}
