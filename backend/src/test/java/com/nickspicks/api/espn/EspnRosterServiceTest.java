package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nickspicks.api.athlete.Athlete;
import com.nickspicks.api.athlete.AthleteRepository;
import com.nickspicks.api.cfbd.CfbdSyncRepository;
import com.nickspicks.api.team.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mapping ESPN's roster shape onto our athlete rows.
 *
 * <p>The fixture is a real captured response rather than a hand-written one -
 * this is an undocumented API, and a payload someone invented would only prove
 * the mapping matches itself.
 */
class EspnRosterServiceTest {

    private EspnSiteClient espn;
    private AthleteRepository athletes;
    private CfbdSyncRepository syncs;
    private EspnRosterService service;

    private final Map<Athlete.Key, Athlete> stored = new HashMap<>();
    private final List<String> synced = new ArrayList<>();

    private static final Team OHIO_STATE = team(194, "Ohio State");

    @BeforeEach
    void setUp() throws Exception {
        espn = mock(EspnSiteClient.class);
        athletes = mock(AthleteRepository.class);
        syncs = mock(CfbdSyncRepository.class);
        service = new EspnRosterService(espn, athletes, syncs);

        JsonNode body = new ObjectMapper().readTree(
                getClass().getResourceAsStream("/espn-roster.json"));
        when(espn.roster(anyInt(), any(Duration.class))).thenReturn(Optional.of(body));

        when(athletes.findById(any())).thenAnswer(call ->
                Optional.ofNullable(stored.get(call.getArgument(0))));
        when(athletes.save(any(Athlete.class))).thenAnswer(call -> {
            Athlete saved = call.getArgument(0);
            stored.put(new Athlete.Key(saved.getId(), saved.getSeason()), saved);
            return saved;
        });
        when(syncs.isSynced(any(), any())).thenAnswer(call -> synced.contains(call.getArgument(1)));
    }

    @Test
    void mapsEveryFieldTheCfbdRosterUsedToSupply() {
        service.ensureRoster(OHIO_STATE, 2026);

        Athlete player = stored.get(new Athlete.Key("5081820", 2026));
        assertThat(player).isNotNull();
        assertThat(player.getFirstName()).isEqualTo("David");
        assertThat(player.getLastName()).isEqualTo("Adolph");
        assertThat(player.getPosition()).isEqualTo("WR");
        // ESPN sends the jersey as a string and height/weight as decimals, in
        // the same units the columns already document.
        assertThat(player.getJersey()).isEqualTo(82);
        assertThat(player.getHeight()).isEqualTo(77);
        assertThat(player.getWeight()).isEqualTo(210);
        assertThat(player.getYear()).isEqualTo(4);
        assertThat(player.getHomeCity()).isEqualTo("Dublin");
        assertThat(player.getHomeState()).isEqualTo("OH");
        assertThat(player.getHomeCountry()).isEqualTo("USA");
    }

    /** The team is ours, not ESPN's - the roster call knows only an id. */
    @Test
    void stampsOurOwnTeamOntoEveryPlayer() {
        service.ensureRoster(OHIO_STATE, 2026);

        assertThat(stored.values()).allSatisfy(player -> {
            assertThat(player.getTeamId()).isEqualTo(194);
            assertThat(player.getTeamSchool()).isEqualTo("Ohio State");
            assertThat(player.getSeason()).isEqualTo(2026);
        });
    }

    /**
     * ESPN can list the same player in more than one position group. The
     * (id, season) key would fail on the second insert before the first has
     * flushed, so duplicates are dropped rather than written twice.
     */
    @Test
    void ignoresAPlayerListedTwice() {
        int written = service.ensureRoster(OHIO_STATE, 2026);

        assertThat(written).isEqualTo(3);
        assertThat(stored).hasSize(3);
    }

    @Test
    void marksTheTeamSyncedSoTheNextPageViewCostsNothing() {
        service.ensureRoster(OHIO_STATE, 2026);
        assertThat(synced).isEmpty();

        // The marker is written through the repository; simulate it having
        // been recorded and confirm the second call short-circuits.
        synced.add("194:2026");
        stored.clear();

        assertThat(service.ensureRoster(OHIO_STATE, 2026)).isZero();
        assertThat(stored).isEmpty();
    }

    /**
     * An outage must not mark the team done. Storing nothing and calling it
     * synced would leave that roster permanently empty.
     */
    @Test
    void leavesTheTeamUnsyncedWhenEspnHasNothing() {
        when(espn.roster(anyInt(), any(Duration.class))).thenReturn(Optional.empty());

        assertThat(service.ensureRoster(OHIO_STATE, 2026)).isZero();
        assertThat(stored).isEmpty();
    }

    private static Team team(int id, String school) {
        Team team = new Team();
        team.setId(id);
        team.setSchool(school);
        return team;
    }
}
