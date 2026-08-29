package com.nickspicks.api.leaderboard;

import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboard;
    private final CurrentWeekResolver weeks;

    public LeaderboardController(LeaderboardService leaderboard, CurrentWeekResolver weeks) {
        this.leaderboard = leaderboard;
        this.weeks = weeks;
    }

    /**
     * Every member, ranked. No week means the whole season ("Overall");
     * with a week, both the record and the pick count cover just that week.
     */
    @GetMapping
    public List<ApiDtos.StandingsRow> leaderboard(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer week) {
        return leaderboard.standings(season == null ? weeks.currentSeason() : season, week);
    }
}
