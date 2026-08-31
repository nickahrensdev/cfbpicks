package com.nickspicks.api.leaderboard;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupMember;
import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupRepository;
import com.nickspicks.api.group.GroupRole;
import com.nickspicks.api.group.TestGroups;
import com.nickspicks.api.ingest.GradingService;
import com.nickspicks.api.pick.CadenceEntryRepository;
import com.nickspicks.api.pick.PickAuditRepository;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickService;
import com.nickspicks.api.pick.Selection;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
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
 * The leaderboard lists every member of the group, whether or not they have
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
    private CadenceEntryRepository entries;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GradingService grading;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    private Group group;

    /** The caller owns the group, so they can read its board. */
    @BeforeEach
    void createGroup() {
        users.save(new AppUser(CALLER, "caller@example.com", "caller", "caller"));
        group = groups.save(new Group(CALLER, TestGroups.weeklyPickem()));
        groupMembers.save(new GroupMember(group.getId(), CALLER, GroupRole.OWNER));
    }

    @Override
    protected void cleanUp() {
        audits.deleteAll();
        pickRepository.deleteAll();
        entries.deleteAll();
        groupMembers.deleteAll();
        groups.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void listsMembersWhoHaveNeverPicked() throws Exception {
        member(UUID.randomUUID(), "ghost");

        mockMvc.perform(get("/api/leaderboard?groupId=" + group.getId()).with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].totalPicks").value(0))
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].wins").value(0))
                // Never-picked members still get a rank rather than vanishing.
                .andExpect(jsonPath("$[?(@.displayName == 'ghost')].rank").exists());
    }

    /** Someone in another league does not appear on this one's board. */
    @Test
    void listsOnlyMembersOfTheGroup() throws Exception {
        member(UUID.randomUUID(), "insider");

        UUID outsiderId = UUID.randomUUID();
        users.save(new AppUser(outsiderId, "outsider@example.com", "outsider", "outsider"));

        mockMvc.perform(get("/api/leaderboard?groupId=" + group.getId()).with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName == 'insider')]").exists())
                .andExpect(jsonPath("$[?(@.displayName == 'outsider')]").doesNotExist());
    }

    @Test
    void rejectsSomeoneWhoIsNotInTheGroup() throws Exception {
        UUID strangerId = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

        mockMvc.perform(get("/api/leaderboard?groupId=" + group.getId())
                        .with(jwt().jwt(builder -> builder
                                .subject(strangerId.toString())
                                .claim("email", "stranger@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void weekFilterNarrowsBothTheRecordAndThePickCount() throws Exception {
        UUID user = member(UUID.randomUUID(), "picker");

        Game weekOne = game(801L, 1);
        Game weekTwo = game(802L, 2);

        picks.create(group, user, weekOne.getId(), Selection.HOME);
        picks.create(group, user, weekTwo.getId(), Selection.HOME);

        // Only week one is graded, and it is a win.
        finish(weekOne, 31, 20);
        grading.gradeGame(games.findById(801L).orElseThrow());

        String base = "/api/leaderboard?groupId=" + group.getId();

        mockMvc.perform(get(base).with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(2))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(1));

        mockMvc.perform(get(base + "&week=1").with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(1))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(1));

        mockMvc.perform(get(base + "&week=2").with(caller()))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].totalPicks").value(1))
                .andExpect(jsonPath("$[?(@.displayName == 'picker')].wins").value(0));
    }

    /** The group's own point values decide the total, not a fixed 1/0.5/0. */
    @Test
    void scoringUsesTheGroupsConfiguredPoints() throws Exception {
        // A spread win pays 3, a total win pays 1, a loss costs 1, a push 0.
        Group weighted = groups.save(new Group(CALLER, TestGroups.weighted(
                "Weighted", new BigDecimal("3"), BigDecimal.ONE,
                new BigDecimal("-1"), BigDecimal.ZERO)));
        groupMembers.save(new GroupMember(weighted.getId(), CALLER, GroupRole.OWNER));

        UUID user = UUID.randomUUID();
        users.save(new AppUser(user, "weighted@example.com", "weighted-picker", "weighted-picker"));
        groupMembers.save(new GroupMember(weighted.getId(), user, GroupRole.MEMBER));

        Game won = game(901L, 1);
        Game lost = game(902L, 1);
        won.setOverUnder(new BigDecimal("45.5"));
        games.save(won);

        picks.create(weighted, user, won.getId(), Selection.HOME);   // covers -> +3
        picks.create(weighted, user, lost.getId(), Selection.AWAY);  // fails  -> -1

        finish(won, 31, 20);
        finish(lost, 31, 20);
        grading.gradeGame(games.findById(901L).orElseThrow());
        grading.gradeGame(games.findById(902L).orElseThrow());

        mockMvc.perform(get("/api/leaderboard?groupId=" + weighted.getId()).with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName == 'weighted-picker')].points").value(2.0))
                .andExpect(jsonPath("$[?(@.displayName == 'weighted-picker')].wins").value(1))
                .andExpect(jsonPath("$[?(@.displayName == 'weighted-picker')].losses").value(1));
    }

    private RequestPostProcessor caller() {
        return jwt().jwt(builder -> builder
                .subject(CALLER.toString())
                .claim("email", "caller@example.com")
                .audience(List.of("authenticated")));
    }

    private UUID member(UUID id, String name) {
        users.save(new AppUser(id, name + "@example.com", name, name));
        groupMembers.save(new GroupMember(group.getId(), id, GroupRole.MEMBER));
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
