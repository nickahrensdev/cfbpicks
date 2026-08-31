package com.nickspicks.api.leaderboard;

import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupService;
import com.nickspicks.api.group.LengthType;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboard;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;
    private final GroupService groups;

    public LeaderboardController(LeaderboardService leaderboard, CurrentWeekResolver weeks,
                                 CurrentUserService currentUser, GroupService groups) {
        this.leaderboard = leaderboard;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.groups = groups;
    }

    /**
     * One group's members, ranked. No week means the whole season; with a week,
     * both the record and the pick count cover just that week.
     *
     * <p>How an absent season is read depends on the group. A continuous group
     * has one board that never resets, so no season filter is applied at all. A
     * per-year group defaults to the current season, and passing an older one
     * is how a member looks back at a previous year.
     */
    @GetMapping
    public List<ApiDtos.StandingsRow> leaderboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID groupId,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer week) {
        Group group = groups.requireVisible(groupId, currentUser.resolve(jwt));

        Integer resolvedSeason = season;
        if (resolvedSeason == null && group.getLengthType() == LengthType.PER_YEAR) {
            resolvedSeason = weeks.currentSeason();
        }
        return leaderboard.standings(group, resolvedSeason, week);
    }
}
