package com.nickspicks.api.season;

import com.fasterxml.jackson.databind.JsonNode;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.espn.EspnSiteClient;
import com.nickspicks.api.game.GameRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

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

    /**
     * ESPN publishes where the season actually is, so ask rather than infer.
     *
     * <p>Fifteen minutes: the answer changes once a week, and the client
     * caches by path, so this costs one unmetered request a quarter of an hour
     * however many people are on the board.
     */
    private static final Duration TTL = Duration.ofMinutes(15);

    /** ESPN's season types. 1 preseason, 2 regular, 3 postseason, 4 offseason. */
    private static final int REGULAR_SEASON = 2;

    private final GameRepository games;
    private final SeasonWeekRepository seasonWeeks;
    private final AppProperties properties;
    private final EspnSiteClient espn;

    public CurrentWeekResolver(GameRepository games, SeasonWeekRepository seasonWeeks,
                               AppProperties properties, EspnSiteClient espn) {
        this.games = games;
        this.seasonWeeks = seasonWeeks;
        this.properties = properties;
        this.espn = espn;
    }

    /**
     * Where ESPN says the season is, when it is a regular-season week.
     *
     * <p>Restricted to type 2 deliberately. Postseason week numbers restart at
     * 1, so a bowl-week answer would collide with the opening week of the
     * regular season and send the board back to August. Preseason and
     * offseason have the same problem from the other end.
     *
     * <p>Everything below falls back when this is empty, so ESPN being
     * unreachable degrades to the behaviour that was here before rather than
     * to an error.
     */
    private Optional<JsonNode> espnPosition() {
        return espn.seasonPosition(TTL)
                .filter(board -> board.path("season").path("type").asInt() == REGULAR_SEASON)
                .filter(board -> board.path("season").path("year").asInt() > 0)
                .filter(board -> board.path("week").path("number").asInt() > 0);
    }

    /**
     * The season being played.
     *
     * <p>Taken from ESPN so it rolls over on its own; {@code app.pickem.season}
     * is the fallback, and the only thing that answers during the months ESPN
     * reports an off- or postseason.
     */
    public int currentSeason() {
        return espnPosition()
                .map(board -> board.path("season").path("year").asInt())
                .orElseGet(() -> properties.getPickem().getSeason());
    }

    /**
     * The first week whose games have not all kicked off, falling back to the
     * last week of the season.
     */
    public int currentWeek() {
        Optional<JsonNode> position = espnPosition();
        if (position.isPresent()) {
            return position.get().path("week").path("number").asInt();
        }
        return currentWeekFromSchedule();
    }

    /**
     * The week worked out from kickoff times, for when ESPN cannot be asked.
     *
     * <p>What this did before ESPN was consulted, kept whole: the first week
     * whose games have not all started, where "started" allows eight hours for
     * the game to actually be played - otherwise the board would move on while
     * the last game of the week was still being watched.
     */
    private int currentWeekFromSchedule() {
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
