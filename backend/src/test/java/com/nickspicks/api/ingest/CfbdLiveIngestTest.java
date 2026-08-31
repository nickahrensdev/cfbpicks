package com.nickspicks.api.ingest;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.athlete.AthleteRepository;
import com.nickspicks.api.coach.CoachRepository;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test against the real CollegeFootballData API.
 *
 * <p>Opt-in, because it spends roughly four calls from a 1,000-a-month
 * allowance and depends on the network:
 *
 * <pre>
 *   mvnw test -Dtest=CfbdLiveIngestTest -Dcfbd.live=true -Dcfbd.key=YOUR_KEY
 * </pre>
 *
 * <p>Worth running after changing anything in the ingest mapping - a renamed
 * field upstream fails silently otherwise, leaving nulls in the database
 * rather than throwing.
 */
@EnabledIfSystemProperty(named = "cfbd.live", matches = "true")
@TestPropertySource(properties = {
        "app.cfbd.enabled=true",
        "app.cfbd.api-key=${cfbd.key:}"
})
class CfbdLiveIngestTest extends IntegrationTest {

    @Autowired
    private ReferenceIngestService referenceIngest;

    @Autowired
    private GameIngestService gameIngest;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private GameRepository games;

    @Autowired
    private AthleteRepository athletes;

    @Autowired
    private CoachRepository coaches;

    @Autowired
    private com.nickspicks.api.ranking.PollRankingRepository pollRankings;

    @Override
    protected void cleanUp() {
        // Left in place - each assertion below tolerates existing rows.
    }

    @Test
    void ingestsTeamsGamesAndLines() {
        referenceIngest.ingestTeams(2026);

        List<Team> allTeams = teams.findAll();
        // FBS (138) plus FCS (128) for 2026.
        assertThat(allTeams).hasSizeGreaterThan(200);
        assertThat(allTeams).allSatisfy(team -> assertThat(team.getSchool()).isNotBlank());
        assertThat(allTeams).anySatisfy(team ->
                assertThat(team.getClassification()).isEqualTo("fcs"));
        assertThat(allTeams).anySatisfy(team ->
                assertThat(team.getClassification()).isEqualTo("fbs"));
        // Smaller programs can be missing a conference or a logo; FBS is not.
        assertThat(allTeams).filteredOn(team -> "fbs".equals(team.getClassification()))
                .allSatisfy(team -> {
                    assertThat(team.getConference()).isNotBlank();
                    assertThat(team.getLogoUrl()).startsWith("https://");
                });

        // One call brings in the whole season, not just one week.
        int ingested = gameIngest.ingestSchedule(2026);
        assertThat(ingested).isGreaterThan(500);
        assertThat(games.findWeeks(2026)).hasSizeGreaterThan(10);

        List<Game> week1 = games.findAllBySeasonAndWeekOrderByKickoffAsc(2026, 1);
        assertThat(week1).isNotEmpty();
        assertThat(week1).allSatisfy(game -> {
            assertThat(game.getHomeTeam()).isNotBlank();
            assertThat(game.getAwayTeam()).isNotBlank();
            assertThat(game.getKickoff()).isNotNull();
        });
        // Team ids are what make the UI's team links work without a lookup.
        assertThat(week1).anySatisfy(game -> assertThat(game.getHomeTeamId()).isNotNull());

        gameIngest.ingestLines(2026);
        assertThat(games.findAllBySeasonAndWeekOrderByKickoffAsc(2026, 1))
                .anySatisfy(game -> assertThat(game.getHomeSpread()).isNotNull());
    }

    @Test
    void ingestsCoaches() {
        referenceIngest.ingestTeams(2026);
        referenceIngest.ingestCoaches(2026);

        assertThat(coaches.findAll()).isNotEmpty();
    }

    /** One call returns every week and every poll; only three are kept. */
    @Test
    void ingestsPollRankings() {
        // 2025 is a completed season, so the committee rankings exist.
        int rows = referenceIngest.ingestRankings(2025);
        assertThat(rows).isGreaterThan(100);

        assertThat(pollRankings.findAll())
                .extracting(com.nickspicks.api.ranking.PollRanking::getPoll)
                .doesNotContain("FCS Coaches Poll", "AFCA Division II Coaches Poll")
                .contains("AP Top 25", "Coaches Poll", "Playoff Committee Rankings");

        // Re-running must update rather than duplicate.
        int again = referenceIngest.ingestRankings(2025);
        assertThat(again).isEqualTo(rows);
    }

    private Team school(String name) {
        return teams.findAll().stream()
                .filter(t -> name.equals(t.getSchool()))
                .findFirst()
                .orElseThrow();
    }
}
