package com.nickspicks.api.season;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.espn.EspnSiteClient;
import com.nickspicks.api.game.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where the season is.
 *
 * <p>ESPN publishes it, so the app asks rather than inferring it from kickoff
 * times - which is what made the week roll over on its own. The inference is
 * still there underneath for when ESPN cannot be reached, and most of what
 * matters here is that the two do not get confused with each other.
 */
class CurrentWeekResolverTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private GameRepository games;
    private SeasonWeekRepository seasonWeeks;
    private EspnSiteClient espn;
    private CurrentWeekResolver resolver;

    @BeforeEach
    void setUp() {
        games = mock(GameRepository.class);
        seasonWeeks = mock(SeasonWeekRepository.class);
        espn = mock(EspnSiteClient.class);

        AppProperties properties = new AppProperties();
        properties.getPickem().setSeason(2026);

        resolver = new CurrentWeekResolver(games, seasonWeeks, properties, espn);
    }

    private void espnSays(int year, int type, int week) {
        try {
            JsonNode board = JSON.readTree("""
                    {"season": {"year": %d, "type": %d}, "week": {"number": %d}}
                    """.formatted(year, type, week));
            when(espn.seasonPosition(any(Duration.class))).thenReturn(Optional.of(board));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void espnUnreachable() {
        when(espn.seasonPosition(any(Duration.class))).thenReturn(Optional.empty());
    }

    @Test
    void takesTheWeekAndSeasonFromEspn() {
        espnSays(2026, 2, 7);

        assertThat(resolver.currentWeek()).isEqualTo(7);
        assertThat(resolver.currentSeason()).isEqualTo(2026);
    }

    /** The point of the change: no config edit, no deploy, no manual step. */
    @Test
    void followsEspnIntoTheNextSeasonWithoutBeingTold() {
        espnSays(2027, 2, 1);

        assertThat(resolver.currentSeason())
                .as("the configured season is 2026; ESPN has moved on")
                .isEqualTo(2027);
        assertThat(resolver.currentWeek()).isEqualTo(1);
    }

    /**
     * Postseason week numbers restart at 1. Trusting one would throw the board
     * back to the opening weekend of the season in the middle of the bowls.
     */
    @Test
    void ignoresEspnOutsideTheRegularSeason() {
        for (int type : new int[] {1, 3, 4}) {
            espnSays(2026, type, 1);
            when(games.findWeeks(2026)).thenReturn(List.of(4, 5, 6));
            when(seasonWeeks.findAllBySeasonAndSeasonTypeOrderByWeekAsc(2026, "regular"))
                    .thenReturn(List.of());

            assertThat(resolver.currentWeek())
                    .as("season type %d is not a regular-season week", type)
                    .isNotEqualTo(1);
        }
    }

    @Test
    void keepsTheConfiguredSeasonWhenEspnIsUnreachable() {
        espnUnreachable();
        when(seasonWeeks.findAllBySeasonAndSeasonTypeOrderByWeekAsc(2026, "regular"))
                .thenReturn(List.of());
        when(games.findWeeks(2026)).thenReturn(List.of());

        assertThat(resolver.currentSeason()).isEqualTo(2026);
    }

    /**
     * The old inference, still doing its job when ESPN cannot be asked - a
     * fallback that had quietly stopped working would only be discovered
     * during an outage.
     */
    @Test
    void fallsBackToTheScheduleWhenEspnIsUnreachable() {
        espnUnreachable();

        SeasonWeek finished = seasonWeek(3, java.time.Instant.now().minus(Duration.ofDays(2)));
        SeasonWeek upcoming = seasonWeek(4, java.time.Instant.now().plus(Duration.ofDays(2)));
        when(seasonWeeks.findAllBySeasonAndSeasonTypeOrderByWeekAsc(2026, "regular"))
                .thenReturn(List.of(finished, upcoming));

        assertThat(resolver.currentWeek())
                .as("week 3's games are over, so the board is on week 4")
                .isEqualTo(4);
    }

    /** A week still being played is not over - see the eight-hour allowance. */
    @Test
    void staysOnAWeekWhoseLastGameIsStillBeingPlayed() {
        espnUnreachable();

        SeasonWeek kickedOffAnHourAgo =
                seasonWeek(3, java.time.Instant.now().minus(Duration.ofHours(1)));
        SeasonWeek next = seasonWeek(4, java.time.Instant.now().plus(Duration.ofDays(6)));
        when(seasonWeeks.findAllBySeasonAndSeasonTypeOrderByWeekAsc(2026, "regular"))
                .thenReturn(List.of(kickedOffAnHourAgo, next));

        assertThat(resolver.currentWeek()).isEqualTo(3);
    }

    private static SeasonWeek seasonWeek(int week, java.time.Instant lastGameStart) {
        SeasonWeek row = mock(SeasonWeek.class);
        when(row.getWeek()).thenReturn(week);
        when(row.getLastGameStart()).thenReturn(lastGameStart);
        return row;
    }
}
