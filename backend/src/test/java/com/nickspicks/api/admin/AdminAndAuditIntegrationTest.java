package com.nickspicks.api.admin;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.pick.PickAuditRepository;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.WeeklyEntryRepository;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role enforcement on the admin surface, and the pick audit trail.
 * admin@example.com is in app.admin-emails for the test profile, so that
 * account is promoted on first request.
 */
class AdminAndAuditIntegrationTest extends IntegrationTest {

    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Autowired
    private GameRepository games;

    @Autowired
    private PickRepository picks;

    @Autowired
    private WeeklyEntryRepository entries;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PickAuditRepository audits;

    @Override
    protected void cleanUp() {
        audits.deleteAll();
        picks.deleteAll();
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void membersCannotReachTheAdminSurface() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/quota").with(member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/activity").with(member()))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuredEmailIsPromotedAndCanManageRoles() throws Exception {
        mockMvc.perform(get("/api/me").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Provision the member, then promote them.
        mockMvc.perform(get("/api/me").with(member()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(put("/api/admin/users/" + MEMBER + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/api/me").with(member()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminsCannotDemoteOrDeleteThemselves() throws Exception {
        mockMvc.perform(get("/api/me").with(admin()));

        mockMvc.perform(put("/api/admin/users/" + ADMIN + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"MEMBER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/" + ADMIN).with(admin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pickLifecycleLeavesAFullAuditTrail() throws Exception {
        Game game = openGame(700L);

        String pickId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/picks").with(member())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gameId\": 700, \"selection\": \"HOME\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.pick.id");

        // Line moves, member re-locks by editing.
        game.setHomeSpread(new BigDecimal("-3.0"));
        games.save(game);
        mockMvc.perform(put("/api/picks/" + pickId).with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selection\": \"AWAY\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/picks/" + pickId).with(member()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/activity").with(admin()))
                .andExpect(status().isOk())
                // Newest first: CANCEL, UPDATE, CREATE.
                .andExpect(jsonPath("$[0].action").value("CANCEL"))
                .andExpect(jsonPath("$[1].action").value("UPDATE"))
                .andExpect(jsonPath("$[1].previousSelection").value("HOME"))
                .andExpect(jsonPath("$[1].previousLockedLine").value(-7.5))
                .andExpect(jsonPath("$[1].lockedLine").value(-3.0))
                .andExpect(jsonPath("$[1].market").value("SPREAD"))
                .andExpect(jsonPath("$[2].action").value("CREATE"))
                .andExpect(jsonPath("$[2].lockedLine").value(-7.5));

        // The audit survives even though the pick itself is gone.
        org.assertj.core.api.Assertions.assertThat(picks.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(audits.findAll()).hasSize(3);
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(builder -> builder
                .subject(ADMIN.toString())
                .claim("email", "admin@example.com")
                .audience(List.of("authenticated")));
    }

    private RequestPostProcessor member() {
        return jwt().jwt(builder -> builder
                .subject(MEMBER.toString())
                .claim("email", "member@example.com")
                .audience(List.of("authenticated")));
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
