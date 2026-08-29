package com.nickspicks.api.season;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.GameRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Works out which week the site should be showing, and which weeks may be
 * browsed.
 *
 * <p>Driven by kickoff times rather than the calendar date, because college
 * football weeks are not seven days long - they slide around for holidays and
 * championship weekends.
 */
@Service
public class CurrentWeekResolver {

    private final GameRepository games;
    private final SeasonWeekRepository seasonWeeks;
    private final AppProperties properties;

    public CurrentWeekResolver(GameRepository games, SeasonWeekRepository seasonWeeks,
                               AppProperties properties) {
        this.games = games;
        this.seasonWeeks = seasonWeeks;
        this.properties = properties;
    }

    public int currentSeason() {
        return properties.getPickem().getSeason();
    }

    /**
     * The first week whose games have not all kicked off, falling back to the
     * last week of the season.
     */
    public int currentWeek() {
        int season = currentSeason();
        Instant cutoff = Instant.now().minus(8, ChronoUnit.HOURS);

        // Prefer the calendar, which knows about weeks with no games loaded.
        List<SeasonWeek> calendar = seasonWeeks
                .findAllBySeasonAndSeasonTypeOrderByWeekAsc(season, "regular");

        if (!calendar.isEmpty()) {
            return calendar.stream()
                    .filter(week -> week.getLastGameStart() == null
                            || week.getLastGameStart().isAfter(cutoff))
                    .map(SeasonWeek::getWeek)
                    .findFirst()
                    .orElseGet(() -> calendar.get(calendar.size() - 1).getWeek());
        }

        List<Integer> loaded = games.findWeeks(season);
        if (loaded.isEmpty()) {
            return 1;
        }
        return loaded.stream()
                .filter(week -> {
                    Instant first = games.findFirstKickoff(season, week);
                    return first != null && first.isAfter(cutoff);
                })
                .findFirst()
                .orElseGet(() -> loaded.get(loaded.size() - 1));
    }

    /**
     * Every week in the season, not just the ones already ingested - members
     * need to be able to look ahead. Falls back to loaded weeks when the
     * calendar has not been ingested.
     */
    public List<Integer> availableWeeks() {
        List<SeasonWeek> calendar = seasonWeeks
                .findAllBySeasonAndSeasonTypeOrderByWeekAsc(currentSeason(), "regular");

        return calendar.isEmpty()
                ? games.findWeeks(currentSeason())
                : calendar.stream().map(SeasonWeek::getWeek).toList();
    }

    /** Weeks that actually have games loaded, so the UI can flag the rest. */
    public List<Integer> loadedWeeks() {
        return games.findWeeks(currentSeason());
    }
}
