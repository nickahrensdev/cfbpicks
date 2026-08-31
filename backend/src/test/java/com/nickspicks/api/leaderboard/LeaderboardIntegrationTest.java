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
import com.nickspicks.api.pick.Pick;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickResult;
import com.nickspicks.api.pick.PickService;
import com.nickspicks.api.pick.Selection;
import com.nickspicks.api.pick.CadenceEntryRepository;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.web.ApiDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The full chain: pick, game goes final, grade, standings, ranking. */
class LeaderboardIntegrationTest extends IntegrationTest {

    @Autowired
    private PickService picks;

    @Autowired
    private PickRepository pickRepository;

    @Autowired
    private CadenceEntryRepository entries;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    /** The league every member below plays in. */
    private Group group;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GradingService grading;

    @Autowired
    private LeaderboardService standings;

    /**
     * The owner is deliberately not enrolled as a group_member here. The
     * standings read group_member, so enrolling them would put a 0-0-0 row on
     * every board below and turn each exact assertion into an exercise in
     * ignoring it. Every member these tests care about joins through
     * {@link #member}.
     */
    @BeforeEach
    void createGroup() {
        UUID owner = UUID.randomUUID();
        users.save(new AppUser(owner, "owner@example.com", "owner", "owner"));
        group = groups.save(new Group(owner, TestGroups.weeklyPickem()));
    }

    @Override
    protected void cleanUp() {
        pickRepository.deleteAll();
        entries.deleteAll();
        groupMembers.deleteAll();
        groups.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    /**
     * Ranking is points first, then most wins, then fewest losses. The three
     * members below are built so that each key in turn is the one doing the
     * work - an ordering that only the full rule produces.
     */
    @Test
    void ranksByPointsThenWinsThenFewestLosses() {
        UUID tieBroken = member("a-tiebroken");   // 2W 1L 0T -> 2.0 pts
        UUID topDog = member("b-topdog");         // 2W 0L 1T -> 2.5 pts
        UUID grinder = member("c-grinder");       // 1W 0L 2T -> 2.0 pts

        // Every game is home -7.5. Home covers in game one (31-20) and fails
        // to cover in game two (24-20), so HOME and AWAY split them.
        Game one = game(1L);
        Game two = game(2L);
        Game three = game(3L);
        Game four = game(4L);
        Game five = game(5L);

        // topdog: wins both, then ties the third.
        picks.create(group, topDog, one.getId(), Selection.HOME);
        picks.create(group, topDog, two.getId(), Selection.AWAY);
        picks.create(group, topDog, three.getId(), Selection.HOME);

        // tiebroken: same two wins, but takes the losing side of game four
        // instead of tying - level on wins with topdog, half a point behind.
        picks.create(group, tieBroken, one.getId(), Selection.HOME);
        picks.create(group, tieBroken, two.getId(), Selection.AWAY);
        picks.create(group, tieBroken, four.getId(), Selection.AWAY);

        // grinder: one win and two ties. Same 2.0 points as tiebroken, but
        // fewer wins, so the second key puts them below.
        picks.create(group, grinder, one.getId(), Selection.HOME);
        picks.create(group, grinder, three.getId(), Selection.AWAY);
        picks.create(group, grinder, five.getId(), Selection.HOME);

        finish(one, 31, 20);    // home covers -7.5
        finish(two, 24, 20);    // home wins but fails to cover
        finishExact(three, 27, 20, new BigDecimal("-7.0")); // lands on 7 - tie
        finish(four, 31, 20);   // home covers, so AWAY loses
        finishExact(five, 27, 20, new BigDecimal("-7.0"));  // lands on 7 - tie

        List.of(1L, 2L, 3L, 4L, 5L).forEach(id ->
                grading.gradeGame(games.findById(id).orElseThrow()));

        List<ApiDtos.StandingsRow> table = standings.standings(group, 2026, null);

        assertThat(table).extracting(ApiDtos.StandingsRow::displayName)
                .containsExactly("b-topdog", "a-tiebroken", "c-grinder");

        // Points carry the halves: a win is 1, a tie is 0.5, a loss is 0.
        assertThat(table).extracting(ApiDtos.StandingsRow::points)
                .containsExactly(2.5, 2.0, 2.0);

        ApiDtos.StandingsRow top = table.get(0);
        assertThat(top.wins()).isEqualTo(2);
        assertThat(top.losses()).isZero();
        assertThat(top.pushes()).isEqualTo(1);

        // Level on points, split by wins - so they are ranked apart, not
        // sharing a rank the way an identical record would.
        assertThat(table.get(1).points()).isEqualTo(table.get(2).points());
        assertThat(table.get(1).wins()).isEqualTo(2);
        assertThat(table.get(2).wins()).isEqualTo(1);
        assertThat(table.get(1).rank()).isEqualTo(2);
        assertThat(table.get(2).rank()).isEqualTo(3);

        // Names are deliberately alphabetical against the expected order, so
        // this could not pass on the display-name fallback alone.
        assertThat(top.displayName()).isEqualTo("b-topdog");
    }

    /**
     * A tie is worth half a point and shows in the record, where a loss is
     * worth none - the distinction the W-L-T column exists to make.
     */
    @Test
    void aTieIsWorthHalfAPointAndALossNone() {
        UUID tier = member("tier");
        UUID loser = member("loser");

        Game tie = game(20L);
        Game lost = game(21L);

        picks.create(group, tier, tie.getId(), Selection.HOME);
        picks.create(group, loser, lost.getId(), Selection.AWAY);

        finishExact(tie, 27, 20, new BigDecimal("-7.0")); // lands on 7 - tie
        finish(lost, 31, 20);                            // home covers, AWAY loses

        grading.gradeGame(games.findById(20L).orElseThrow());
        grading.gradeGame(games.findById(21L).orElseThrow());

        List<ApiDtos.StandingsRow> table = standings.standings(group, 2026, null);

        ApiDtos.StandingsRow tierRow = row(table, "tier");
        assertThat(tierRow.pushes()).isEqualTo(1);
        assertThat(tierRow.losses()).isZero();
        assertThat(tierRow.points()).isEqualTo(0.5);

        ApiDtos.StandingsRow loserRow = row(table, "loser");
        assertThat(loserRow.losses()).isEqualTo(1);
        assertThat(loserRow.pushes()).isZero();
        assertThat(loserRow.points()).isZero();

        assertThat(tierRow.rank()).isLessThan(loserRow.rank());
    }

    @Test
    void voidsPicksOnCanceledGamesInsteadOfCountingThemAsLosses() {
        UUID user = member("unlucky");
        Game canceled = game(10L);
        picks.create(group, user, canceled.getId(), Selection.HOME);

        canceled.setStatus(GameStatus.CANCELED);
        games.save(canceled);
        grading.gradeGame(canceled);

        assertThat(pickRepository.findAllByGameId(10L))
                .extracting(Pick::getResult)
                .containsExactly(PickResult.VOID);

        // The member still stands, on an empty record - a canceled game is
        // not a loss for anyone, and it earns no points either.
        ApiDtos.StandingsRow unlucky = row(standings.standings(group, 2026, null), "unlucky");
        assertThat(unlucky.wins()).isZero();
        assertThat(unlucky.losses()).isZero();
        assertThat(unlucky.pushes()).isZero();
        assertThat(unlucky.points()).isZero();
        // The pick is still theirs, it just decided nothing.
        assertThat(unlucky.totalPicks()).isEqualTo(1);
    }

    /**
     * gradeGame is idempotent-on-PENDING by design: once a source (ESPN,
     * CFBD) has settled a pick, a later gradeGame call can never touch it
     * again, correct or not. regradeGame is the deliberate way around
     * that - the same math, but applied unconditionally.
     */
    @Test
    void regradeGameOverwritesAnAlreadySettledPickWhenTheScoreIsCorrected() {
        UUID member = member("bettor");
        Game game = game(20L);
        picks.create(group, member, game.getId(), Selection.HOME);

        finish(game, 31, 20); // covers -7.5 -> WIN
        grading.gradeGame(game);
        assertThat(pickRepository.findAllByGameId(20L))
                .extracting(Pick::getResult)
                .containsExactly(PickResult.WIN);

        // A later correction to the score - gradeGame would no-op here since
        // the pick is no longer PENDING.
        Game corrected = games.findById(20L).orElseThrow();
        corrected.setHomeScore(24); // no longer covers -7.5 -> LOSS
        games.save(corrected);

        assertThat(grading.gradeGame(corrected)).isZero();
        assertThat(pickRepository.findAllByGameId(20L))
                .extracting(Pick::getResult)
                .containsExactly(PickResult.WIN); // unchanged - proves the no-op

        assertThat(grading.regradeGame(corrected)).isEqualTo(1);
        assertThat(pickRepository.findAllByGameId(20L))
                .extracting(Pick::getResult)
                .containsExactly(PickResult.LOSS); // regradeGame actually fixed it
    }

    // ------------------------------------------------------------- fixtures

    /** One member's standing by name, so an assertion does not depend on position. */
    private ApiDtos.StandingsRow row(List<ApiDtos.StandingsRow> table, String displayName) {
        return table.stream()
                .filter(row -> displayName.equals(row.displayName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No standings row for " + displayName + " in " + table));
    }

    private UUID member(String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name, name));
        groupMembers.save(new GroupMember(group.getId(), id, GroupRole.MEMBER));
        return id;
    }

    private Game game(long id) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(1);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(Instant.now().plus(2, ChronoUnit.DAYS));
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

    private void finishExact(Game game, int homeScore, int awayScore, BigDecimal spread) {
        // The picks already locked -7.5; move them to the whole number so the
        // push path is exercised.
        pickRepository.findAllByGameId(game.getId()).forEach(pick -> {
            pick.setLockedLine(spread);
            pickRepository.save(pick);
        });
        finish(game, homeScore, awayScore);
    }
}
