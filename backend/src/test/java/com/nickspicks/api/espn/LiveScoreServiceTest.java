package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reading ESPN's scoreboard.
 *
 * <p>The fixtures are trimmed copies of real responses. The shapes matter more
 * than the values: ESPN nests status under the competition, sends scores as
 * strings, and omits {@code situation} entirely outside of a live game, and
 * each of those has its own way of going wrong.
 */
class LiveScoreServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    private LiveScoreService serviceReturning(String body) throws Exception {
        EspnSiteClient client = mock(EspnSiteClient.class);
        JsonNode node = json.readTree(body);
        when(client.scoreboard(any(Duration.class))).thenReturn(Optional.of(node));
        return new LiveScoreService(client);
    }

    @Test
    void readsScoreClockAndPossessionFromALiveGame() throws Exception {
        LiveScoreService service = serviceReturning("""
                {"events":[{"id":"401628455","competitions":[{
                  "status":{"displayClock":"4:21","period":3,
                            "type":{"state":"in","shortDetail":"4:21 - 3rd"}},
                  "situation":{"possession":"194","downDistanceText":"2nd & 7 at OSU 34",
                               "isRedZone":false},
                  "competitors":[{"homeAway":"home","score":"21"},
                                 {"homeAway":"away","score":"17"}]}]}]}
                """);

        Map<Long, LiveScoreService.LiveGame> live = service.current();

        assertThat(live).containsOnlyKeys(401628455L);
        LiveScoreService.LiveGame game = live.get(401628455L);
        assertThat(game.inProgress()).isTrue();
        assertThat(game.homeScore()).isEqualTo(21);
        assertThat(game.awayScore()).isEqualTo(17);
        assertThat(game.clock()).isEqualTo("4:21");
        assertThat(game.period()).isEqualTo(3);
        assertThat(game.periodLabel()).isEqualTo("3rd");
        assertThat(game.possessionTeamId()).isEqualTo(194);
        assertThat(game.downDistance()).isEqualTo("2nd & 7 at OSU 34");
        assertThat(game.redZone()).isFalse();
    }

    @Test
    void aScheduledGameIsReadButNotInProgress() throws Exception {
        LiveScoreService service = serviceReturning("""
                {"events":[{"id":"401856766","competitions":[{
                  "status":{"displayClock":"0:00","period":0,
                            "type":{"state":"pre","shortDetail":"8/29 - 3:00 PM EDT"}},
                  "competitors":[{"homeAway":"home","score":"0"},
                                 {"homeAway":"away","score":"0"}]}]}]}
                """);

        LiveScoreService.LiveGame game = service.current().get(401856766L);

        // Read, so a caller can tell "not started" from "not on the board",
        // but never surfaced as live - the mapper filters on inProgress().
        assertThat(game.inProgress()).isFalse();
        assertThat(game.state()).isEqualTo("pre");
        // No situation node at all before kickoff. The absence must read as
        // null rather than throwing.
        assertThat(game.possessionTeamId()).isNull();
        assertThat(game.downDistance()).isNull();
        assertThat(game.periodLabel()).isNull();
    }

    @Test
    void overtimePeriodsAreLabelledAsOvertime() throws Exception {
        LiveScoreService service = serviceReturning("""
                {"events":[
                  {"id":"1","competitions":[{"status":{"period":5,
                     "type":{"state":"in"}},"competitors":[]}]},
                  {"id":"2","competitions":[{"status":{"period":7,
                     "type":{"state":"in"}},"competitors":[]}]}]}
                """);

        Map<Long, LiveScoreService.LiveGame> live = service.current();

        // A bare "5" beside a clock reads as a fifth quarter.
        assertThat(live.get(1L).periodLabel()).isEqualTo("OT");
        assertThat(live.get(2L).periodLabel()).isEqualTo("3OT");
    }

    @Test
    void anUnreachableScoreboardYieldsNothingRatherThanFailing() {
        EspnSiteClient client = mock(EspnSiteClient.class);
        when(client.scoreboard(any(Duration.class))).thenReturn(Optional.empty());

        assertThat(new LiveScoreService(client).current()).isEmpty();
    }
}
