package com.nickspicks.api.game;

import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;

    public GameController(GameService gameService, CurrentWeekResolver weeks,
                          CurrentUserService currentUser) {
        this.gameService = gameService;
        this.weeks = weeks;
        this.currentUser = currentUser;
    }

    @GetMapping("/weeks/current")
    public ApiDtos.CurrentWeek currentWeek() {
        return new ApiDtos.CurrentWeek(weeks.currentSeason(), weeks.currentWeek(),
                weeks.availableWeeks(), weeks.loadedWeeks());
    }

    @GetMapping("/games")
    public List<ApiDtos.GameSummary> games(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) Integer season,
                                           @RequestParam(required = false) Integer week,
                                           @RequestParam(required = false) String conference,
                                           @RequestParam(required = false) Integer teamId,
                                           @RequestParam(required = false) Double minSpread,
                                           @RequestParam(required = false) Double maxSpread) {
        UUID userId = currentUser.resolveId(jwt);
        return gameService.listWeek(
                season == null ? weeks.currentSeason() : season,
                week == null ? weeks.currentWeek() : week,
                userId,
                new GameService.GameFilter(conference, teamId, minSpread, maxSpread));
    }

    /** Conferences, teams and the widest line in the week being viewed. */
    @GetMapping("/games/filters")
    public ApiDtos.GameFilters filters(@RequestParam(required = false) Integer season,
                                       @RequestParam(required = false) Integer week) {
        return gameService.filterOptions(
                season == null ? weeks.currentSeason() : season,
                week == null ? weeks.currentWeek() : week);
    }

    @GetMapping("/games/{id}")
    public ApiDtos.GameDetail game(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return gameService.detail(id, currentUser.resolveId(jwt));
    }
}
