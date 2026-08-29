package com.nickspicks.api.web;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.WeeklyEntryRepository;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PickApiIntegrationTest extends IntegrationTest {

    private static final UUID MEMBER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private GameRepository games;

    @Autowired
    private PickRepository picks;

    @Autowired
    private WeeklyEntryRepository entries;

    @Autowired
    private AppUserRepository users;

    @Override
    protected void cleanUp() {
        picks.deleteAll();
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/picks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/games")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void metaStaysPublicSoTheUiCanCheckTheEnvironment() throws Exception {
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("test"));
    }

    @Test
    void provisionsAMemberRowOnFirstCall() throws Exception {
        mockMvc.perform(get("/api/me").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER.toString()))
                .andExpect(jsonPath("$.displayName").value("nick"));
    }

    @Test
    void createsAPickAndReportsRemainingSlots() throws Exception {
        Game game = openGame(500L);

        mockMvc.perform(post("/api/picks").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId": %d, "selection": "AWAY"}
                                """.formatted(game.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selection").value("AWAY"))
                .andExpect(jsonPath("$.lockedLine").value(-7.5))
                .andExpect(jsonPath("$.result").value("PENDING"));

        mockMvc.perform(get("/api/picks?season=2026&week=1").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picksUsed").value(1))
                .andExpect(jsonPath("$.picksRemaining").value(9))
                .andExpect(jsonPath("$.maxPicks").value(10));
    }

    @Test
    void returnsAMachineReadableCodeWhenTheWindowHasClosed() throws Exception {
        Game locked = openGame(501L);
        locked.setKickoff(Instant.now().plus(10, ChronoUnit.MINUTES));
        games.save(locked);

        mockMvc.perform(post("/api/picks").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId": %d, "selection": "HOME"}
                                """.formatted(locked.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PICK_WINDOW_CLOSED"));
    }

    @Test
    void marksLockedGamesInTheWeekListing() throws Exception {
        openGame(502L);
        Game soon = openGame(503L);
        soon.setKickoff(Instant.now().plus(5, ChronoUnit.MINUTES));
        games.save(soon);

        mockMvc.perform(get("/api/games?season=2026&week=1").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 502)].locked").value(false))
                .andExpect(jsonPath("$[?(@.id == 503)].locked").value(true));
    }

    @Test
    void hidesMemberPicksOnAGameThatHasNotStarted() throws Exception {
        Game game = openGame(504L);

        mockMvc.perform(post("/api/picks").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId": %d, "selection": "HOME"}
                                """.formatted(game.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/games/" + game.getId()).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picksRevealed").value(false))
                .andExpect(jsonPath("$.memberPicks").isEmpty())
                // The caller always sees their own pick.
                .andExpect(jsonPath("$.game.mySpreadPick.selection").value("HOME"))
                .andExpect(jsonPath("$.game.myTotalPick").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor member() {
        return jwt().jwt(builder -> builder
                .subject(MEMBER.toString())
                .claim("email", "nick@example.com")
                .audience(java.util.List.of("authenticated")));
    }

    private Game openGame(long id) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(1);
        game.setHomeTeam("Iowa State");
        game.setAwayTeam("Kansas");
        game.setKickoff(Instant.now().plus(3, ChronoUnit.DAYS));
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }
}
