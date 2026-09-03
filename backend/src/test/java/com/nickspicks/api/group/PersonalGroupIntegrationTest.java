package com.nickspicks.api.group;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The board that comes with an account.
 *
 * <p>Its whole point is that it cannot be got at: the refusals are the
 * feature, so they are what this covers. A personal board that could be
 * shared, joined or edited would be an ordinary group that happens to start
 * with one member.
 */
class PersonalGroupIntegrationTest extends IntegrationTest {

    private static final UUID OWNER = UUID.fromString("dddd0000-0000-0000-0000-000000000001");
    private static final UUID OUTSIDER = UUID.fromString("dddd0000-0000-0000-0000-000000000002");

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository members;

    @Autowired
    private AppUserRepository users;

    @Override
    protected void cleanUp() {
        members.deleteAll();
        groups.deleteAll();
        users.deleteAll();
    }

    /** The first authenticated request an account ever makes creates it. */
    @Test
    void arrivesWithTheAccount() throws Exception {
        // Any authenticated call will do - the account, and its board, are
        // created by CurrentUserService resolving the token.
        mockMvc.perform(get("/api/groups/mine").with(owner())).andExpect(status().isOk());

        List<Group> mine = groups.findAll().stream().filter(Group::isPersonal).toList();
        assertThat(mine).hasSize(1);

        Group board = mine.get(0);
        assertThat(board.getCreatedBy()).isEqualTo(OWNER);
        assertThat(board.getName()).isEqualTo(PersonalGroups.NAME);
        assertThat(board.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(board.isPersonal()).isTrue();

        // Exactly one member, who owns it.
        assertThat(members.findAllByGroupId(board.getId()))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getUserId()).isEqualTo(OWNER);
                    assertThat(member.getRole()).isEqualTo(GroupRole.OWNER);
                });
    }

    /** The settings the feature promises, asserted rather than assumed. */
    @Test
    void isAnUnlimitedPickemWithEveryMarketAndAFiveMinuteLock() throws Exception {
        Group board = board();

        assertThat(board.getGroupType()).isEqualTo(GroupType.PICKEM);
        assertThat(board.getLockLeadMinutes()).isEqualTo(5);

        assertThat(board.isMoneylineEnabled()).isTrue();
        assertThat(board.isSpreadEnabled()).isTrue();
        assertThat(board.isTotalEnabled()).isTrue();

        // No limit of any kind - that is what "no limits" has to mean.
        assertThat(board.getMaxPicksPerCadence()).isNull();
        assertThat(board.getMinPicksPerCadence()).isZero();
        assertThat(board.getTeamPickLimit()).isNull();
        assertThat(board.getMoneylineMaxPerCadence()).isNull();
        assertThat(board.getSpreadMaxPerCadence()).isNull();
        assertThat(board.getTotalMaxPerCadence()).isNull();
    }

    /**
     * A moneyline has to be worth less than a spread, or taking every heavy
     * favourite would be the only sensible way to play a board with no cap.
     */
    @Test
    void pricesAMoneylineBelowASpread() throws Exception {
        Group board = board();

        assertThat(board.getSpreadWinPoints()).isEqualByComparingTo("1");
        assertThat(board.getTotalWinPoints()).isEqualByComparingTo("1");

        assertThat(board.getMoneylineWinPoints()).isEqualByComparingTo("0.5");
        // The negative is the part that stops volume winning.
        assertThat(board.getMoneylineLossPoints()).isEqualByComparingTo("-0.5");
    }

    @Test
    void cannotBeEditedEvenByItsOwner() throws Exception {
        mockMvc.perform(put("/api/groups/" + board().getId())
                        .with(owner())
                        .contentType("application/json")
                        .content(settingsJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/groups/" + board().getId()).with(owner()))
                .andExpect(status().isForbidden());

        assertThat(groups.findById(board().getId())).isPresent();
    }

    @Test
    void cannotBeShared() throws Exception {
        mockMvc.perform(post("/api/groups/" + board().getId() + "/share").with(owner()))
                .andExpect(status().isForbidden());
    }

    /** Its owner cannot leave either - that would strand the account. */
    @Test
    void cannotBeLeft() throws Exception {
        mockMvc.perform(delete("/api/groups/" + board().getId() + "/members/" + OWNER)
                        .with(owner()))
                .andExpect(status().isForbidden());
    }

    /** Private, so it is not in search - and a stranger cannot read it either. */
    @Test
    void isInvisibleToEveryoneElse() throws Exception {
        UUID id = board().getId();

        mockMvc.perform(get("/api/groups/" + id).with(outsider()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/search?q=").with(outsider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + PersonalGroups.NAME + "')]").isEmpty());
    }

    /** The owner sees it, but with nothing to configure. */
    @Test
    void reportsItselfAsUnmanageableAndUnshareable() throws Exception {
        mockMvc.perform(get("/api/groups/" + board().getId()).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personal").value(true))
                .andExpect(jsonPath("$.manageable").value(false))
                .andExpect(jsonPath("$.shareable").value(false));
    }

    // ------------------------------------------------------------------ setup

    /** Touches an endpoint so the account - and its board - come into being. */
    private Group board() throws Exception {
        mockMvc.perform(get("/api/groups/mine").with(owner()));
        return groups.findByCreatedByAndPersonalTrue(OWNER).orElseThrow();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return jwtFor(OWNER, "owner@example.com");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor outsider() {
        return jwtFor(OUTSIDER, "outsider@example.com");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(
            UUID id, String email) {
        return jwt().jwt(builder -> builder
                .subject(id.toString())
                .claim("email", email));
    }

    /** Any valid payload - it never gets as far as being read. */
    private String settingsJson() {
        return """
                {"name": "Renamed", "visibility": "PRIVATE", "groupType": "PICKEM",
                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                 "lockLeadMinutes": 30, "minPicksPerCadence": 0,
                 "multiplePicksPerGame": true, "requireApproval": false,
                 "shareableByMembers": false,
                 "moneylineEnabled": true, "spreadEnabled": true, "totalEnabled": true,
                 "moneylineWinPoints": 1, "moneylineLossPoints": 0, "moneylinePushPoints": 0.5,
                 "spreadWinPoints": 1, "spreadLossPoints": 0, "spreadPushPoints": 0.5,
                 "totalWinPoints": 1, "totalLossPoints": 0, "totalPushPoints": 0.5}
                """;
    }
}
