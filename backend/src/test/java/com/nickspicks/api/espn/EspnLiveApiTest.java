package com.nickspicks.api.espn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test against the real ESPN endpoints.
 *
 * <p>Opt-in because it needs the network:
 *
 * <pre>
 *   mvnw test -Dtest=EspnLiveApiTest -Despn.live=true
 * </pre>
 *
 * <p>ESPN publishes no contract for these endpoints, so a field can be renamed
 * or dropped without warning. The failure mode is silent - nulls arrive where
 * data used to - which is exactly what this test is for. Worth running after
 * touching any of the ESPN mapping.
 *
 * <p>Unlike the CollegeFootballData equivalent it costs no quota, only
 * politeness, so run it freely.
 */
@EnabledIfSystemProperty(named = "espn.live", matches = "true")
class EspnLiveApiTest {

    /** Alabama. A program that will not stop existing between test runs. */
    private static final int TEAM_ID = 333;

    /** C.J. Stroud - retired from college, so his record no longer changes. */
    private static final String ATHLETE_ID = "4432577";

    private final EspnClient core = new EspnClient();
    private final EspnSiteClient site = new EspnSiteClient();

    @Test
    void teamCarriesBrandingAndVenue() {
        Optional<EspnDtos.EspnTeam> found = core.team(TEAM_ID);

        assertThat(found).isPresent();
        EspnDtos.EspnTeam team = found.get();
        assertThat(team.displayName()).isEqualTo("Alabama Crimson Tide");
        assertThat(team.abbreviation()).isEqualTo("ALA");
        // The hash matters: ESPN sends the colour bare and CSS needs it.
        assertThat(team.color()).startsWith("#");
        assertThat(team.logoUrl()).contains("333");
        assertThat(team.venueName()).isEqualTo("Bryant-Denny Stadium");
        assertThat(team.venueCity()).isEqualTo("Tuscaloosa");
        assertThat(team.espnUrl()).contains("espn.com");
    }

    @Test
    void athleteCarriesBiography() {
        Optional<EspnDtos.EspnAthlete> found = core.athlete(ATHLETE_ID);

        assertThat(found).isPresent();
        EspnDtos.EspnAthlete athlete = found.get();
        assertThat(athlete.displayName()).isEqualTo("C.J. Stroud");
        assertThat(athlete.position()).isEqualTo("Quarterback");
        assertThat(athlete.displayHeight()).isNotBlank();
        assertThat(athlete.headshotUrl()).contains(ATHLETE_ID);
        assertThat(athlete.birthCountry()).isNotBlank();
    }

    @Test
    void anUnknownIdIsEmptyRatherThanAnError() {
        // A 404 has to read as "no data", not as a failure - plenty of our
        // athletes predate ESPN's coverage.
        assertThat(core.athlete("0")).isEmpty();
    }

    @Test
    void scoreboardReturnsAWholeSlateOfGames() {
        LiveScoreService live = new LiveScoreService(site);

        // The default page size is 25; the request asks for 200 so a full
        // Saturday is not silently truncated. Out of season this is small,
        // hence the loose bound.
        assertThat(live.current()).isNotEmpty();
        assertThat(live.current().values())
                .allSatisfy(game -> assertThat(game.state()).isIn("pre", "in", "post"));
    }

    @Test
    void summaryCarriesBoxScoreAndLeaders() {
        // A finished game, so its box score is stable.
        Optional<EspnGameService.EspnGame> found = new EspnGameService(site).summary(401628455L);

        assertThat(found).isPresent();
        EspnGameService.EspnGame game = found.get();
        assertThat(game.venueName()).isNotBlank();
        assertThat(game.teamStats()).hasSize(2);
        assertThat(game.teamStats().getFirst().stats()).isNotEmpty();
        assertThat(game.leaders()).isNotEmpty();
        // Athlete ids are what make a leader's name a link to their page.
        assertThat(game.leaders().getFirst().leaders())
                .allSatisfy(leader -> assertThat(leader.athleteId()).isNotBlank());
    }
}
