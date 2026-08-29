package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRepository;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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

    public PickController(PickService picks, GameRepository games, CurrentWeekResolver weeks,
                          CurrentUserService currentUser, DtoMapper mapper,
                          AppProperties properties, PickWindow window, TeamRepository teams) {
        this.picks = picks;
        this.games = games;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.properties = properties;
        this.window = window;
        this.teams = teams;
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
    public ResponseEntity<ApiDtos.PickSummary> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApiDtos.CreatePickRequest request) {

        Pick pick = picks.create(currentUser.resolveId(jwt), request.gameId(), request.selection(),
                request.expectedLine());
        return ResponseEntity.created(URI.create("/api/picks/" + pick.getId()))
                .body(mapper.pickSummary(pick));
    }

    @PutMapping("/picks/{id}")
    public ApiDtos.PickSummary update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody ApiDtos.UpdatePickRequest request) {
        return mapper.pickSummary(picks.update(currentUser.resolveId(jwt), id,
                request.selection(), request.expectedLine()));
    }

    /**
     * Re-lock onto the game's current line, keeping the same side. Rejected
     * unless the line actually improved.
     */
    @PostMapping("/picks/{id}/relock")
    public ApiDtos.PickSummary relock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.pickSummary(picks.relock(currentUser.resolveId(jwt), id));
    }

    @DeleteMapping("/picks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        picks.delete(currentUser.resolveId(jwt), id);
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
