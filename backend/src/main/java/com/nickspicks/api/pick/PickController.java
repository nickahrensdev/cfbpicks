package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
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

    public PickController(PickService picks, GameRepository games, CurrentWeekResolver weeks,
                          CurrentUserService currentUser, DtoMapper mapper,
                          AppProperties properties, PickWindow window, TeamRepository teams,
                          RankingService rankings) {
        this.picks = picks;
        this.games = games;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.properties = properties;
        this.window = window;
        this.teams = teams;
        this.rankings = rankings;
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
    private ApiDtos.GameSummary gameSummaryFor(UUID userId, Game game) {
        List<Pick> myPicks = picks.findForUserGame(userId, game.getId());
        Map<Integer, Integer> ranks = rankings.rankLookup(game.getSeason(), game.getWeek());
        return mapper.gameSummary(game, myPicks, null, ranks);
    }

    private Game requireGame(Long gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));
    }

    @GetMapping("/picks")
    public ApiDtos.WeekPicks myPicks(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) Integer season,
                                     @RequestParam(required = false) Integer week) {
        UUID userId = currentUser.resolveId(jwt);
        int resolvedSeason = season == null ? weeks.currentSeason() : season;
        int resolvedWeek = week == null ? weeks.currentWeek() : week;

        List<ApiDtos.PickWithGame> rows = withGames(
                picks.findForUserWeek(userId, resolvedSeason, resolvedWeek));

        int max = properties.getPickem().getMaxPicksPerWeek();
        int remaining = picks.remainingPicks(userId, resolvedSeason, resolvedWeek);

        return new ApiDtos.WeekPicks(resolvedSeason, resolvedWeek, max - remaining, remaining, max,
                rows);
    }

    @PostMapping("/picks")
    public ResponseEntity<ApiDtos.PickResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApiDtos.CreatePickRequest request) {
        UUID userId = currentUser.resolveId(jwt);

        Pick pick = picks.create(userId, request.gameId(), request.selection(),
                request.expectedLine());
        Game game = requireGame(pick.getGameId());
        return ResponseEntity.created(URI.create("/api/picks/" + pick.getId()))
                .body(new ApiDtos.PickResponse(mapper.pickSummary(pick), gameSummaryFor(userId, game)));
    }

    @PutMapping("/picks/{id}")
    public ApiDtos.PickResponse update(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody ApiDtos.UpdatePickRequest request) {
        UUID userId = currentUser.resolveId(jwt);

        Pick pick = picks.update(userId, id, request.selection(), request.expectedLine());
        Game game = requireGame(pick.getGameId());
        return new ApiDtos.PickResponse(mapper.pickSummary(pick), gameSummaryFor(userId, game));
    }

    /**
     * Re-lock onto the game's current line, keeping the same side. Rejected
     * unless the line actually improved.
     */
    @PostMapping("/picks/{id}/relock")
    public ApiDtos.PickResponse relock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = currentUser.resolveId(jwt);

        Pick pick = picks.relock(userId, id);
        Game game = requireGame(pick.getGameId());
        return new ApiDtos.PickResponse(mapper.pickSummary(pick), gameSummaryFor(userId, game));
    }

    /** Returns the game's updated card state rather than 204 - there is no pick left to report. */
    @DeleteMapping("/picks/{id}")
    public ApiDtos.GameSummary delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = currentUser.resolveId(jwt);
        Game game = picks.delete(userId, id);
        return gameSummaryFor(userId, game);
    }

    /**
     * Another member's card. Games that have not kicked off are filtered out
     * server-side.
     */
    @GetMapping("/members/{userId}/picks")
    public List<ApiDtos.PickWithGame> memberPicks(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID userId,
                                                  @RequestParam(required = false) Integer season,
                                                  @RequestParam(required = false) Integer week) {
        // Resolve the caller so an unprovisioned member still gets a row.
        currentUser.resolveId(jwt);

        int resolvedSeason = season == null ? weeks.currentSeason() : season;
        int resolvedWeek = week == null ? weeks.currentWeek() : week;

        return withGames(picks.findRevealedForUserWeek(userId, resolvedSeason, resolvedWeek));
    }

    /**
     * Attaches each pick's game.
     *
     * <p>Games and teams are fetched once for the whole list rather than per
     * pick - ten picks would otherwise cost ten game lookups plus twenty team
     * lookups to render one page.
     */
    private List<ApiDtos.PickWithGame> withGames(List<Pick> picksToMap) {
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
                                    : mapper.gameSummary(game, List.of(pick), teamCache),
                            game != null
                                    && window.isOpen(game)
                                    && window.isLineImproved(pick, game));
                })
                .toList();
    }
}
