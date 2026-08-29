package com.nickspicks.api.leaderboard;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.ingest.GradingService;
import com.nickspicks.api.pick.PickAuditRepository;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickService;
import com.nickspicks.api.pick.Selection;
import com.nickspicks.api.pick.WeeklyEntryRepository;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The leaderboard lists every signed-up member, whether or not they have
 * picked, and can be narrowed to a single week.
 */
class LeaderboardApiIntegrationTest extends IntegrationTest {

    private static final UUID CALLER = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Autowired
    private PickService picks;

    @Autowired
    private PickRepository pickRepository;

    @Autowired
    private PickAuditRepository audits;

    @Autowired
    private WeeklyEntryRepository entries;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GradingService grading;

    @Override
    protected void cleanUp() {
        audits.deleteAll();
        pickRepository.deleteAll();
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void listsMembersWhoHaveNeverPicked() throws Exception {
        member(UUID.randomUUID(), "ghost");

        mockMvc.perform(get("/api/leaderboard").with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].totalPicks").value(0))
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].wins").value(0))
                // Never-picked members still get a rank rather than vanishing.
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].rank").exists());
    }

    @Test
    void weekFilterNarrowsBothTheRecordAndThePickCount() throws Exception {
        UUID user = member(UUID.randomUUID(), "picker");

        Game weekOne = game(801L, 1);
        Game weekTwo = game(802L, 2);

        picks.create(user, weekOne.getId(), Selection.HOME);
        picks.create(user, weekTwo.getId(), Selection.HOME);

        // Only week one is graded, and it is a win.
        finish(weekOne, 31, 20);
        grading.gradeGame(games.findById(801L).orElseThrow());

        mockMvc.perform(get("/api/leaderboard").with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(2))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(1));

        mockMvc.perform(get("/api/leaderboard?week=1").with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(1))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(1));

        mockMvc.perform(get("/api/leaderboard?week=2").with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(1))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(0));
    }

    private RequestPostProcessor caller() {
        return jwt().jwt(builder -> builder
                .subject(CALLER.toString())
                .claim("email", "caller@example.com")
                .audience(List.of("authenticated")));
    }

    private UUID member(UUID id, String name) {
        users.save(new AppUser(id, name + "@example.com", name));
        return id;
    }

    private Game game(long id, int week) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(week);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(Instant.now().plus(3, ChronoUnit.DAYS));
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }

    private void finish(Game game, int homeScore, int awayScore) {
        game.setHomeScore(homeScore);
        game.setAwayScore(awayScore);
        game.setStatus(GameStatus.FINAL);
        games.save(game);
    }
}
