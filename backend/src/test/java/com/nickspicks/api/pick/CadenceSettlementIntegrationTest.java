package com.nickspicks.api.pick;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupMember;
import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupRepository;
import com.nickspicks.api.group.GroupRole;
import com.nickspicks.api.group.GroupSettings;
import com.nickspicks.api.group.GroupType;
import com.nickspicks.api.group.LengthType;
import com.nickspicks.api.group.TestGroups;
import com.nickspicks.api.group.Visibility;
import com.nickspicks.api.leaderboard.LeaderboardService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.web.ApiDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settling a closed period: who finished short of a minimum, what it cost, and
 * how it reaches the standings.
 *
 * <p>The distinction this whole path exists for: a maximum refuses the pick
 * that would break it, but a member who has picked nothing yet has not broken a
 * minimum - they are early. So a minimum is charged after the fact, once the
 * period can no longer take a pick.
 */
class CadenceSettlementIntegrationTest extends IntegrationTest {

    /** Predates every fixture's schedule, so no period is skipped as too early. */
    private static final java.time.LocalDate RUNNING_ALL_ALONG = java.time.LocalDate.of(2000, 1, 1);

    @Autowired
    private PickRepository pickRepository;

    @Autowired
    private CadenceEntryRepository entries;

    @Autowired
    private PickAuditRepository audits;

    @Autowired
    private CadenceSettlementService settlement;

    @Autowired
    private CadenceSettlementRepository settlements;

    @Autowired
    private CadencePenaltyRepository penalties;

    @Autowired
    private LeaderboardService standings;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    private int names;

    @Override
    protected void cleanUp() {
        penalties.deleteAll();
        settlements.deleteAll();
        audits.deleteAll();
        pickRepository.deleteAll();
        entries.deleteAll();
        groupMembers.deleteAll();
        groups.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void chargesTheDifferenceWhenAPeriodClosesShortOfAMarketMinimum() {
        Group group = group(minimums(2, null, null, null));
        UUID member = member(group, "short");

        // Week 1 has kicked off and taken one spread pick of the two required.
        Game played = pastGame(1L, 1);
        pick(group, member, played, Selection.HOME);

        assertThat(settlement.settle(group, 2026)).isEqualTo(1);

        List<CadencePenalty> charged = penalties.findAllByGroupIdAndUserId(group.getId(), member);
        assertThat(charged).singleElement()
                .satisfies(penalty -> {
                    assertThat(penalty.getMarket()).isEqualTo(Market.SPREAD);
                    assertThat(penalty.getShortfall()).isEqualTo(1);
                    assertThat(penalty.getPeriodKey()).isEqualTo("2026-W01");
                });
    }

    @Test
    void chargesNothingWhenTheMinimumWasMet() {
        Group group = group(minimums(2, null, null, null));
        UUID member = member(group, "met");

        pick(group, member, pastGame(1L, 1), Selection.HOME);
        pick(group, member, pastGame(2L, 1), Selection.AWAY);

        assertThat(settlement.settle(group, 2026)).isEqualTo(1);
        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), member)).isEmpty();
    }

    /**
     * A period is closed when its last game has kicked off. Until then a member
     * who is short can still fix it, so charging them would be charging for
     * something that has not happened.
     */
    @Test
    void leavesAPeriodAloneUntilItsLastGameHasKickedOff() {
        Group group = group(minimums(2, null, null, null));
        UUID member = member(group, "early");

        pastGame(1L, 1);
        // Still to come, so week 1 is not closed.
        game(2L, 1, Instant.now().plus(2, ChronoUnit.DAYS));

        assertThat(settlement.settle(group, 2026)).isZero();
        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), member)).isEmpty();
    }

    /** Idempotence: the hourly job must not re-charge a period it has closed. */
    @Test
    void settlesAPeriodOnlyOnce() {
        Group group = group(minimums(2, null, null, null));
        UUID member = member(group, "once");
        pastGame(1L, 1);

        assertThat(settlement.settle(group, 2026)).isEqualTo(1);
        assertThat(settlement.settle(group, 2026)).isZero();

        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), member))
                .singleElement()
                .satisfies(penalty -> assertThat(penalty.getShortfall()).isEqualTo(2));
    }

    @Test
    void chargesEachMarketAgainstItsOwnMinimum() {
        Group group = group(minimums(1, 1, 1, null));
        UUID member = member(group, "spread-only");

        pick(group, member, pastGame(1L, 1), Selection.HOME);

        settlement.settle(group, 2026);

        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), member))
                .extracting(CadencePenalty::getMarket)
                .containsExactlyInAnyOrder(Market.TOTAL, Market.MONEYLINE);
    }

    /**
     * The overall minimum names no market, so it is priced at the harshest loss
     * the group uses - otherwise skipping a pick could be cheaper than making a
     * bad one, which would make sitting out a strategy.
     */
    @Test
    void pricesTheOverallMinimumAtTheHarshestLossTheGroupUses() {
        Group group = group(overallMinimum(3, new BigDecimal("-2")));
        UUID member = member(group, "overall");

        pick(group, member, pastGame(1L, 1), Selection.HOME);

        settlement.settle(group, 2026);

        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), member))
                .singleElement()
                .satisfies(penalty -> {
                    assertThat(penalty.getMarket()).isNull();
                    assertThat(penalty.getShortfall()).isEqualTo(2);
                    // Two missed picks at the -2 the group charges for a loss.
                    assertThat(penalty.getPoints()).isEqualByComparingTo("-4");
                });
    }

    @Test
    void aChargedMinimumShowsOnTheLeaderboardAsALoss() {
        Group group = group(minimums(2, null, null, null));
        UUID member = member(group, "penalised");

        pick(group, member, pastGame(1L, 1), Selection.HOME);
        settlement.settle(group, 2026);

        ApiDtos.StandingsRow row = standings.standings(group, 2026, null).stream()
                .filter(entry -> entry.userId().equals(member))
                .findFirst()
                .orElseThrow();

        // The pick itself is still pending, so the only loss on the card is the
        // charged one.
        assertThat(row.losses()).isEqualTo(1);
        assertThat(row.penaltyLosses()).isEqualTo(1);
        assertThat(row.totalPicks()).isEqualTo(1);
    }

    @Test
    void settlesEachMemberOfTheGroupSeparately() {
        Group group = group(minimums(1, null, null, null));
        UUID met = member(group, "did");
        UUID missed = member(group, "didnt");

        pick(group, met, pastGame(1L, 1), Selection.HOME);
        settlement.settle(group, 2026);

        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), met)).isEmpty();
        assertThat(penalties.findAllByGroupIdAndUserId(group.getId(), missed)).hasSize(1);
    }

    /** A group with nothing to require is never settled - and stays settleable. */
    @Test
    void skipsAGroupWithNoMinimumsAtAll() {
        Group group = group(TestGroups.weeklyPickem());
        member(group, "free");
        pastGame(1L, 1);

        assertThat(settlement.settle(group, 2026)).isZero();
        assertThat(settlements.findAllByGroupId(group.getId())).isEmpty();
    }

    // ------------------------------------------------------------- fixtures

    /**
     * A group created after the season has begun is not answerable for the
     * weeks it did not exist for.
     *
     * <p>Settlement builds its periods from the schedule, so without a start
     * date it charged every closed week of the season the moment a group was
     * created. In a points league that is a pile of unearned losses; in an
     * elimination pool at two strikes it puts everyone out before a single
     * pick can be made, which made starting one mid-season impossible.
     */
    @Test
    void doesNotChargePeriodsThatFinishedBeforeTheGroupStarted() {
        Instant lastWeek = Instant.now().minus(9, ChronoUnit.DAYS);
        game(900L, 1, lastWeek);

        // Starts today; week 1 is already behind it.
        Group group = group(startingOn(java.time.LocalDate.now()));
        UUID member = member(group, "latecomer");

        settlement.settle(group, 2026);

        assertThat(penalties.findAllByGroupIdAndPeriodKey(group.getId(), "2026-W01"))
                .as("a week that ended before the group began is not its to charge")
                .isEmpty();

        // Recorded as settled all the same, so the skip is not recomputed on
        // every run for the rest of the season.
        assertThat(settlements.settledKeys(group.getId())).contains("2026-W01");
        assertThat(member).isNotNull();
    }

    /** The other half: a week that is still running when the group starts. */
    @Test
    void stillChargesAPeriodTheGroupWasAliveFor() {
        game(901L, 2, Instant.now().minus(2, ChronoUnit.HOURS));

        Group group = group(startingOn(java.time.LocalDate.now().minusDays(1)));
        UUID member = member(group, "present");

        settlement.settle(group, 2026);

        assertThat(penalties.findAllByGroupIdAndPeriodKey(group.getId(), "2026-W02"))
                .anySatisfy(penalty -> assertThat(penalty.getUserId()).isEqualTo(member));
    }

    /** A weekly pickem group with an overall minimum and the given start date. */
    private GroupSettings startingOn(java.time.LocalDate startsOn) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal half = new BigDecimal("0.5");

        return new GroupSettings(
                "Started", null, Visibility.PUBLIC, null,
                GroupType.PICKEM, Cadence.WEEKLY, LengthType.CONTINUOUS, 2026,
                30, 10, 1, true, false, false,
                true, true, true,
                null, null, null, null, null, null,
                one, zero, half,
                one, zero, half,
                one, zero, half,
                null, null, null,
                startsOn, false);
    }

    /** A weekly pickem group with the given per-market minimums. */
    private GroupSettings minimums(Integer spreadMin, Integer totalMin, Integer moneylineMin,
                                   Integer overall) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal half = new BigDecimal("0.5");

        return new GroupSettings(
                "Minimums", null, Visibility.PUBLIC, null,
                GroupType.PICKEM, Cadence.WEEKLY, LengthType.CONTINUOUS, 2026,
                30, 10, overall == null ? 0 : overall, true, false, false,
                true, true, true,
                moneylineMin, null, spreadMin, null, totalMin, null,
                one, zero, half,
                one, zero, half,
                one, zero, half,
                null, null, null,
                // Running since long before the fixtures' games, so nothing is
                // skipped as predating the group - see startingOn() for the
                // tests that are about the start date itself.
                RUNNING_ALL_ALONG, false);
    }

    /**
     * A group whose only requirement is an overall minimum, with a loss priced
     * below zero so the "harshest loss" rule has something to find.
     *
     * <p>Deliberately a pickem group: the overall minimum used to be dropped
     * for this type, back when the only consequence of missing one was being
     * eliminated. Charging it as losses works just as well in a points league.
     */
    private GroupSettings overallMinimum(int minimum, BigDecimal lossPoints) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal half = new BigDecimal("0.5");

        return new GroupSettings(
                "Overall", null, Visibility.PUBLIC, null,
                GroupType.PICKEM, Cadence.WEEKLY, LengthType.CONTINUOUS, 2026,
                30, 10, minimum, true, false, false,
                // Moneyline is off, so its 0-point loss is not the harshest the
                // group uses - the -2 on the two live markets is.
                false, true, true,
                null, null, null, null, null, null,
                one, lossPoints, half,
                one, lossPoints, half,
                one, BigDecimal.ZERO, half,
                null, null, null,
                RUNNING_ALL_ALONG, false);
    }

    private Group group(GroupSettings settings) {
        UUID owner = UUID.randomUUID();
        String name = "owner" + names++;
        users.save(new AppUser(owner, name + "@example.com", name, name));
        Group saved = groups.save(new Group(owner, settings));
        groupMembers.save(new GroupMember(saved.getId(), owner, GroupRole.OWNER));
        return saved;
    }

    private UUID member(Group group, String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name, name));
        groupMembers.save(new GroupMember(group.getId(), id, GroupRole.MEMBER));
        return id;
    }

    /**
     * A pick on a game that has already kicked off.
     *
     * <p>Written straight to the repository: PickService would refuse it, which
     * is exactly the rule that makes a closed period's counts final.
     */
    private void pick(Group group, UUID userId, Game game, Selection selection) {
        Pick pick = new Pick();
        pick.setGroupId(group.getId());
        pick.setUserId(userId);
        pick.setGameId(game.getId());
        pick.setSelection(selection);
        pick.setLockedLine(selection.market() == Market.TOTAL
                ? game.getOverUnder()
                : game.getHomeSpread());
        pickRepository.save(pick);
    }

    private Game pastGame(long id, int week) {
        return game(id, week, Instant.now().minus(4, ChronoUnit.HOURS));
    }

    private Game game(long id, int week, Instant kickoff) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(week);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(kickoff);
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setOverUnder(new BigDecimal("45.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }
}
