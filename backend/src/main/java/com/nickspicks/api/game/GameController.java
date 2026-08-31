package com.nickspicks.api.game;

import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;
    private final GroupService groups;

    public GameController(GameService gameService, CurrentWeekResolver weeks,
                          CurrentUserService currentUser, GroupService groups) {
        this.gameService = gameService;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.groups = groups;
    }

    /** The calendar, which is the same whichever group you are playing in. */
    @GetMapping("/weeks/current")
    public ApiDtos.CurrentWeek currentWeek() {
        return new ApiDtos.CurrentWeek(weeks.currentSeason(), weeks.currentWeek(),
                weeks.availableWeeks(), weeks.loadedWeeks());
    }

    /**
     * The board, as seen from one group: your picks in that group, and that
     * group's lock times.
     *
     * @param date one game day, for a group that picks daily. Takes precedence
     *             over {@code week} - a daily group's board is a day, and a
     *             week would show games from several allowances at once.
     */
    @GetMapping("/games")
    public List<ApiDtos.GameSummary> games(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam UUID groupId,
                                           @RequestParam(required = false) Integer season,
                                           @RequestParam(required = false) Integer week,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                           LocalDate date,
                                           @RequestParam(required = false) String conference,
                                           @RequestParam(required = false) Integer teamId,
                                           @RequestParam(required = false) Double minSpread,
                                           @RequestParam(required = false) Double maxSpread) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);
        GameService.GameFilter filter =
                new GameService.GameFilter(conference, teamId, minSpread, maxSpread);

        if (date != null) {
            return gameService.listDay(group, date, userId, filter);
        }

        return gameService.listWeek(
                group,
                season == null ? weeks.currentSeason() : season,
                week == null ? weeks.currentWeek() : week,
                userId,
                filter);
    }

    /** Days in the season with games, so a daily board's date picker can land somewhere useful. */
    @GetMapping("/games/days")
    public List<LocalDate> gameDays(@RequestParam(required = false) Integer season) {
        return gameService.gameDays(season == null ? weeks.currentSeason() : season);
    }

    /**
     * Conferences, teams and the widest line in the week being viewed. Derived
     * from the schedule alone, so it needs no group.
     */
    @GetMapping("/games/filters")
    public ApiDtos.GameFilters filters(@RequestParam(required = false) Integer season,
                                       @RequestParam(required = false) Integer week,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                       LocalDate date) {
        if (date != null) {
            return gameService.filterOptionsForDay(date);
        }
        return gameService.filterOptions(
                season == null ? weeks.currentSeason() : season,
                week == null ? weeks.currentWeek() : week);
    }

    @GetMapping("/games/{id}")
    public ApiDtos.GameDetail game(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable long id,
                                   @RequestParam UUID groupId) {
        UUID userId = currentUser.resolveId(jwt);
        Group group = groups.requirePlayable(groupId, userId);
        return gameService.detail(group, id, userId);
    }
}
