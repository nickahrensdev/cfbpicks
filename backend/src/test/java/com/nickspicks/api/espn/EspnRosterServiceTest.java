package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nickspicks.api.web.ApiDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reading ESPN's roster shape into the summaries a team page renders.
 *
 * <p>The fixture is a real captured response. For an undocumented API a
 * hand-written payload would only prove the mapping matches itself.
 */
class EspnRosterServiceTest {

    private static final ApiDtos.TeamSummary OHIO_STATE = new ApiDtos.TeamSummary(
            194, "Ohio State", "Buckeyes", "OSU", "Big Ten", null, null, null);

    private EspnSiteClient site;
    private EspnRosterService service;

    @BeforeEach
    void setUp() throws Exception {
        site = mock(EspnSiteClient.class);
        service = new EspnRosterService(site, mock(EspnClient.class));

        JsonNode body = new ObjectMapper().readTree(
                getClass().getResourceAsStream("/espn-roster.json"));
        when(site.roster(anyInt(), any(Duration.class))).thenReturn(Optional.of(body));
    }

    @Test
    void mapsEveryFieldTheRosterTabRenders() {
        ApiDtos.AthleteSummary player = service.roster(194, OHIO_STATE).stream()
                .filter(p -> p.id().equals("5081820"))
                .findFirst()
                .orElseThrow();

        assertThat(player.firstName()).isEqualTo("David");
        assertThat(player.lastName()).isEqualTo("Adolph");
        assertThat(player.position()).isEqualTo("WR");
        // ESPN sends the jersey as a string and the class year nested.
        assertThat(player.jersey()).isEqualTo(82);
        assertThat(player.year()).isEqualTo(4);
        assertThat(player.headshotUrl())
                .isEqualTo("https://a.espncdn.com/i/headshots/college-football/players/full/5081820.png");
        assertThat(player.team()).isEqualTo(OHIO_STATE);
    }

    /** One player in the fixture has none, as one in 120 does upstream. */
    @Test
    void leavesTheHeadshotNullWhenEspnHasNoPortrait() {
        assertThat(service.roster(194, OHIO_STATE))
                .anySatisfy(player -> assertThat(player.headshotUrl()).isNull());
    }

    /**
     * ESPN can list one player under two position groups, and the fixture
     * repeats the first deliberately.
     */
    @Test
    void ignoresAPlayerListedTwice() {
        List<ApiDtos.AthleteSummary> roster = service.roster(194, OHIO_STATE);

        assertThat(roster).hasSize(3);
        assertThat(roster).extracting(ApiDtos.AthleteSummary::id).doesNotHaveDuplicates();
    }

    /** Jersey order, unnumbered last - how a printed roster reads. */
    @Test
    void ordersByJerseyNumber() {
        assertThat(service.roster(194, OHIO_STATE))
                .extracting(ApiDtos.AthleteSummary::jersey)
                .isSortedAccordingTo(java.util.Comparator.nullsLast(
                        java.util.Comparator.naturalOrder()));
    }

    /**
     * A provider outage costs the roster tab, not the team page - so this
     * answers empty rather than throwing.
     */
    @Test
    void returnsNothingWhenEspnIsUnreachable() {
        when(site.roster(anyInt(), any(Duration.class))).thenReturn(Optional.empty());

        assertThat(service.roster(194, OHIO_STATE)).isEmpty();
    }

    /** An undocumented API changing shape must not read as a team with no players. */
    @Test
    void returnsNothingWhenThePayloadHasNoAthletesArray() throws Exception {
        JsonNode empty = new ObjectMapper().readTree("{\"team\": {\"id\": \"194\"}}");
        when(site.roster(anyInt(), any(Duration.class))).thenReturn(Optional.of(empty));

        assertThat(service.roster(194, OHIO_STATE)).isEmpty();
    }
}
