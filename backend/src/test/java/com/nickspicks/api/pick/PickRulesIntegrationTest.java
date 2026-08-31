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
import com.nickspicks.api.group.TestGroups;
import com.nickspicks.api.pick.PickExceptions.PickWindowClosedException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PickRulesIntegrationTest extends IntegrationTest {

    @Autowired
    private PickService picks;

    @Autowired
    private PickRepository pickRepository;

    @Autowired
    private CadenceEntryRepository entries;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    @Autowired
    private PickAuditRepository audits;

    /** A weekly ten-pick league, matching how the site behaved before groups. */
    private Group group;

    @BeforeEach
    void createGroup() {
        UUID owner = member("owner");
        group = groups.save(new Group(owner, TestGroups.weeklyPickem()));
        groupMembers.save(new GroupMember(group.getId(), owner, GroupRole.OWNER));
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
    void allowsTenPicksAndRejectsTheEleventh() {
        UUID user = member("ten-picks");
        List<Game> week = openGames(11);

        for (int i = 0; i < 10; i++) {
            picks.create(group, user, week.get(i).getId(), Selection.HOME);
        }

        assertThat(picks.remainingPicks(group, user, 2026, 1)).isZero();
        assertThatThrownBy(() -> picks.create(group, user, week.get(10).getId(), Selection.AWAY))
                .isInstanceOf(WeeklyLimitReachedException.class);
        assertThat(pickRepository.findForUserWeek(group.getId(), user, 2026, 1)).hasSize(10);
    }

    /**
     * The reason cadence_entry exists. Without the row lock, both threads read
     * a count of nine and both insert, leaving the member with eleven picks.
     */
    @Test
    void twoConcurrentTenthPicksLeaveExactlyTenPicks() throws Exception {
        UUID user = member("racer");
        List<Game> week = openGames(12);

        for (int i = 0; i < 9; i++) {
            picks.create(group, user, week.get(i).getId(), Selection.HOME);
        }

        int threads = 6;
        CyclicBarrier startLine = new CyclicBarrier(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Long gameId = week.get(9 + (i % 3)).getId();
                tasks.add(() -> {
                    startLine.await();
                    try {
                        picks.create(group, user, gameId, Selection.HOME);
                        accepted.incrementAndGet();
                    } catch (RuntimeException expected) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();
            }
        }

        assertThat(accepted.get() + rejected.get()).isEqualTo(threads);
        assertThat(pickRepository.findForUserWeek(group.getId(), user, 2026, 1)).hasSize(10);
        assertThat(entries.findById(
                new CadenceEntry.Key(group.getId(), user, CadencePeriod.weekly(2026, 1))))
                .get()
                .extracting(CadenceEntry::getPickCount)
                .isEqualTo(10);
    }

    @Test
    void refusesToCreateEditOrCancelInsideTheLockWindow() {
        UUID user = member("late");
        Game open = openGames(1).get(0);
        Pick existing = picks.create(group, user, open.getId(), Selection.HOME);

        // Slide kickoff to 29 minutes away - now inside the 30-minute lock.
        open.setKickoff(Instant.now().plus(29, ChronoUnit.MINUTES));
        games.save(open);

        Game other = game(99L, Instant.now().plus(29, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> picks.create(group, user, other.getId(), Selection.HOME))
                .isInstanceOf(PickWindowClosedException.class);
        assertThatThrownBy(() -> picks.update(group, user, existing.getId(), Selection.AWAY))
                .isInstanceOf(PickWindowClosedException.class);
        assertThatThrownBy(() -> picks.delete(group, user, existing.getId()))
                .isInstanceOf(PickWindowClosedException.class);
    }

    @Test
    void cancellingFreesTheSlot() {
        UUID user = member("canceller");
        List<Game> week = openGames(2);

        Pick first = picks.create(group, user, week.get(0).getId(), Selection.HOME);
        assertThat(picks.remainingPicks(group, user, 2026, 1)).isEqualTo(9);

        picks.delete(group, user, first.getId());
        assertThat(picks.remainingPicks(group, user, 2026, 1)).isEqualTo(10);
    }

    @Test
    void editingRelocksTheCurrentLine() {
        UUID user = member("editor");
        Game game = openGames(1).get(0);

        Pick pick = picks.create(group, user, game.getId(), Selection.HOME);
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");

        game.setHomeSpread(new BigDecimal("-3.0"));
        games.save(game);

        Pick edited = picks.update(group, user, pick.getId(), Selection.AWAY);
        assertThat(edited.getLockedLine()).isEqualByComparingTo("-3.0");
    }

    @Test
    void lineMovementDoesNotDisturbAnExistingPick() {
        UUID user = member("early-bird");
        Game game = openGames(1).get(0);

        Pick pick = picks.create(group, user, game.getId(), Selection.HOME);

        game.setHomeSpread(new BigDecimal("-14.0"));
        games.save(game);

        assertThat(pickRepository.findById(pick.getId()))
                .get()
                .extracting(Pick::getLockedLine)
                .isEqualTo(new BigDecimal("-7.5"));
    }

    @Test
    void relockTakesABetterLineButRefusesAWorseOne() {
        UUID user = member("shopper");
        Game game = openGames(1).get(0);

        Pick pick = picks.create(group, user, game.getId(), Selection.HOME);
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");

        // Line moves against the home side - no re-lock on offer.
        game.setHomeSpread(new BigDecimal("-10.0"));
        games.save(game);
        assertThatThrownBy(() -> picks.relock(group, user, pick.getId()))
                .isInstanceOf(PickExceptions.InvalidPickException.class);
        assertThat(pickRepository.findById(pick.getId()))
                .get().extracting(Pick::getLockedLine).isEqualTo(new BigDecimal("-7.5"));

        // Line moves in their favour - re-lock, same side, better number.
        game.setHomeSpread(new BigDecimal("-2.5"));
        games.save(game);
        Pick relocked = picks.relock(group, user, pick.getId());
        assertThat(relocked.getSelection()).isEqualTo(Selection.HOME);
        assertThat(relocked.getLockedLine()).isEqualByComparingTo("-2.5");
    }

    /**
     * A tab left open while the line moves must not silently place the pick
     * at today's number.
     */
    @Test
    void refusesAPickPlacedAgainstAStaleLine() {
        UUID user = member("stale-tab");
        Game game = openGames(1).get(0);

        // The page was rendered at -7.5; the board has since moved to -10.
        game.setHomeSpread(new BigDecimal("-10.0"));
        games.save(game);

        assertThatThrownBy(() ->
                picks.create(group, user, game.getId(), Selection.HOME, new BigDecimal("-7.5")))
                .isInstanceOf(PickExceptions.LineMovedException.class);
        assertThat(pickRepository.findForUserWeek(group.getId(), user, 2026, 1)).isEmpty();

        // Re-sent with the number actually on screen, it goes through.
        Pick pick = picks.create(group, user, game.getId(), Selection.HOME, new BigDecimal("-10.0"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-10.0");
    }

    @Test
    void trailingZeroesAreTheSameLine() {
        UUID user = member("precision");
        Game game = openGames(1).get(0);

        // -7.5 and -7.50 are the same number; only a real move should conflict.
        Pick pick = picks.create(group, user, game.getId(), Selection.HOME, new BigDecimal("-7.50"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");
    }

    @Test
    void relockIsRefusedOnceTheWindowCloses() {
        UUID user = member("latecomer");
        Game game = openGames(1).get(0);
        Pick pick = picks.create(group, user, game.getId(), Selection.HOME);

        game.setHomeSpread(new BigDecimal("-1.0"));
        game.setKickoff(Instant.now().plus(20, ChronoUnit.MINUTES));
        games.save(game);

        assertThatThrownBy(() -> picks.relock(group, user, pick.getId()))
                .isInstanceOf(PickWindowClosedException.class);
    }

    // ------------------------------------------------------ over/under

    @Test
    void bothMarketsCanBePickedOnTheSameGame() {
        UUID user = member("two-markets");
        Game game = openGames(1).get(0);

        Pick spread = picks.create(group, user, game.getId(), Selection.HOME);
        Pick total = picks.create(group, user, game.getId(), Selection.OVER);

        assertThat(spread.getMarket()).isEqualTo(Market.SPREAD);
        assertThat(spread.getLockedLine()).isEqualByComparingTo("-7.5");
        assertThat(total.getMarket()).isEqualTo(Market.TOTAL);
        // The total pick locks the total, not the spread.
        assertThat(total.getLockedLine()).isEqualByComparingTo("45.5");

        // Both draw on the one shared allowance.
        assertThat(picks.remainingPicks(group, user, 2026, 1)).isEqualTo(8);
    }

    @Test
    void asecondPickOnTheSameMarketIsRejected() {
        UUID user = member("doubler");
        Game game = openGames(1).get(0);

        picks.create(group, user, game.getId(), Selection.OVER);

        assertThatThrownBy(() -> picks.create(group, user, game.getId(), Selection.UNDER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);
        assertThat(pickRepository.findForUserWeek(group.getId(), user, 2026, 1)).hasSize(1);
    }

    /** The cap is one pool, however the picks are split between markets. */
    @Test
    void theWeeklyCapCountsBothMarketsTogether() {
        UUID user = member("mixer");
        List<Game> week = openGames(6);

        for (int i = 0; i < 5; i++) {
            picks.create(group, user, week.get(i).getId(), Selection.HOME);
        }
        for (int i = 0; i < 5; i++) {
            picks.create(group, user, week.get(i).getId(), Selection.OVER);
        }

        assertThat(picks.remainingPicks(group, user, 2026, 1)).isZero();
        assertThatThrownBy(() -> picks.create(group, user, week.get(5).getId(), Selection.HOME))
                .isInstanceOf(WeeklyLimitReachedException.class);
    }

    @Test
    void aTotalPickIsCheckedAgainstTheTotalNotTheSpread() {
        UUID user = member("careful");
        Game game = openGames(1).get(0);

        // Sending the spread as the expected line for a total pick must not
        // be mistaken for a match.
        assertThatThrownBy(() ->
                picks.create(group, user, game.getId(), Selection.OVER, new BigDecimal("-7.5")))
                .isInstanceOf(PickExceptions.LineMovedException.class);

        Pick pick = picks.create(group, user, game.getId(), Selection.OVER, new BigDecimal("45.5"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("45.5");
    }

    @Test
    void switchingMarketsOnAnExistingPickIsRejected() {
        UUID user = member("switcher");
        Game game = openGames(1).get(0);
        Pick pick = picks.create(group, user, game.getId(), Selection.HOME);

        assertThatThrownBy(() -> picks.update(group, user, pick.getId(), Selection.OVER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);

        // Switching within the market is still fine.
        assertThat(picks.update(group, user, pick.getId(), Selection.AWAY).getSelection())
                .isEqualTo(Selection.AWAY);
    }

    @Test
    void aGameWithNoPostedTotalCannotBeTotalPicked() {
        UUID user = member("no-total");
        Game game = openGames(1).get(0);
        game.setOverUnder(null);
        games.save(game);

        assertThatThrownBy(() -> picks.create(group, user, game.getId(), Selection.OVER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);

        // The spread on the same game is unaffected.
        assertThat(picks.create(group, user, game.getId(), Selection.HOME)).isNotNull();
    }

    @Test
    void otherMembersPicksAreHiddenUntilKickoff() {
        UUID user = member("secretive");
        Game upcoming = openGames(1).get(0);
        picks.create(group, user, upcoming.getId(), Selection.HOME);

        assertThat(picks.findRevealedForUser(group.getId(), user, 2026, 1)).isEmpty();

        // Once it kicks off, the pick becomes visible.
        upcoming.setKickoff(Instant.now().minus(5, ChronoUnit.MINUTES));
        games.save(upcoming);

        assertThat(picks.findRevealedForUser(group.getId(), user, 2026, 1)).hasSize(1);
    }

    /**
     * A null week means the whole season. The card defaults to it because the
     * current week moves past the last week anyone picked as soon as a slate
     * finishes - asking for "this week" then showed an empty card for a member
     * with a season behind them.
     */
    @Test
    void aNullWeekReturnsTheWholeSeason() {
        UUID user = member("season-long");

        Game weekOne = game(3001L, 1, Instant.now().plus(3, ChronoUnit.DAYS));
        Game weekTwo = game(3002L, 2, Instant.now().plus(3, ChronoUnit.DAYS));
        picks.create(group, user, weekOne.getId(), Selection.HOME);
        picks.create(group, user, weekTwo.getId(), Selection.HOME);

        // Both have since kicked off, so both are revealed.
        weekOne.setKickoff(Instant.now().minus(5, ChronoUnit.MINUTES));
        weekTwo.setKickoff(Instant.now().minus(5, ChronoUnit.MINUTES));
        games.save(weekOne);
        games.save(weekTwo);

        assertThat(picks.findRevealedForUser(group.getId(), user, 2026, 1)).hasSize(1);
        assertThat(picks.findRevealedForUser(group.getId(), user, 2026, 2)).hasSize(1);
        assertThat(picks.findRevealedForUser(group.getId(), user, 2026, null)).hasSize(2);
    }

    // -------------------------------------------------------------- winner

    /**
     * A winner pick locks nothing. That is the point of the market, and it is
     * what the nullable column and its check constraint exist for.
     */
    @Test
    void aWinnerPickLocksNoLine() {
        UUID user = member("outright");
        Group winners = groups.save(new Group(user,
                TestGroups.settings("Winners", Cadence.WEEKLY, 10, true, true, true)));
        groupMembers.save(new GroupMember(winners.getId(), user, GroupRole.OWNER));

        Game game = openGames(1).get(0);
        Pick pick = picks.create(winners, user, game.getId(), Selection.HOME_WINNER);

        assertThat(pick.getMarket()).isEqualTo(Market.WINNER);
        assertThat(pick.getLockedLine()).isNull();
    }

    /**
     * The winner market needs nothing posted - the teams are playing whether or
     * not a bookmaker has an opinion - so a game with no lines at all is still
     * winner-pickable while the other two markets are not.
     */
    @Test
    void aGameWithNoLinesCanStillBeWinnerPicked() {
        UUID user = member("no-lines");
        Group winners = groups.save(new Group(user,
                TestGroups.settings("Winners", Cadence.WEEKLY, 10, true, true, true)));
        groupMembers.save(new GroupMember(winners.getId(), user, GroupRole.OWNER));

        Game game = openGames(1).get(0);
        game.setHomeSpread(null);
        game.setOverUnder(null);
        games.save(game);

        assertThatThrownBy(() -> picks.create(winners, user, game.getId(), Selection.HOME))
                .isInstanceOf(PickExceptions.InvalidPickException.class);
        assertThat(picks.create(winners, user, game.getId(), Selection.HOME_WINNER)).isNotNull();
    }

    /** All three markets are separate picks on the same game. */
    @Test
    void allThreeMarketsCanBeHeldOnOneGame() {
        UUID user = member("collector");
        Group winners = groups.save(new Group(user,
                TestGroups.settings("Winners", Cadence.WEEKLY, 10, true, true, true)));
        groupMembers.save(new GroupMember(winners.getId(), user, GroupRole.OWNER));

        Game game = openGames(1).get(0);
        picks.create(winners, user, game.getId(), Selection.HOME);
        picks.create(winners, user, game.getId(), Selection.OVER);
        picks.create(winners, user, game.getId(), Selection.HOME_WINNER);

        assertThat(pickRepository.findForUserWeek(winners.getId(), user, 2026, 1))
                .extracting(Pick::getMarket)
                .containsExactlyInAnyOrder(Market.SPREAD, Market.TOTAL, Market.WINNER);

        // Still one per market, though.
        assertThatThrownBy(() ->
                picks.create(winners, user, game.getId(), Selection.AWAY_WINNER))
                .isInstanceOf(PickExceptions.InvalidPickException.class)
                .hasMessageContaining("already picked a winner");
    }

    /** A group that does not play winners refuses one, as with any market. */
    @Test
    void aWinnerPickIsRefusedWhenTheGroupDoesNotPlayIt() {
        UUID user = member("spread-only-league");
        Game game = openGames(1).get(0);

        // The default fixture leaves the winner market off.
        assertThatThrownBy(() -> picks.create(group, user, game.getId(), Selection.HOME_WINNER))
                .isInstanceOf(PickExceptions.InvalidPickException.class)
                .hasMessageContaining("does not play winners");
    }

    // ------------------------------------------------------------- groups

    /** The whole point of the group column: two leagues, one game, two picks. */
    @Test
    void theSameGameCanBePickedInTwoGroups() {
        UUID user = member("two-leagues");
        Game game = openGames(1).get(0);
        Group other = groups.save(new Group(user, TestGroups.weeklyPickem()));
        groupMembers.save(new GroupMember(other.getId(), user, GroupRole.OWNER));

        Pick here = picks.create(group, user, game.getId(), Selection.HOME);
        Pick there = picks.create(other, user, game.getId(), Selection.AWAY);

        assertThat(here.getGroupId()).isEqualTo(group.getId());
        assertThat(there.getGroupId()).isEqualTo(other.getId());
        assertThat(pickRepository.findForUserWeek(group.getId(), user, 2026, 1)).hasSize(1);
        assertThat(pickRepository.findForUserWeek(other.getId(), user, 2026, 1)).hasSize(1);
    }

    /** Allowances are per group, so picks in one league do not spend another's. */
    @Test
    void eachGroupHasItsOwnAllowance() {
        UUID user = member("budgeter");
        List<Game> week = openGames(3);
        Group other = groups.save(new Group(user, TestGroups.settings("Small", Cadence.WEEKLY, 1)));
        groupMembers.save(new GroupMember(other.getId(), user, GroupRole.OWNER));

        picks.create(group, user, week.get(0).getId(), Selection.HOME);
        picks.create(other, user, week.get(0).getId(), Selection.HOME);

        assertThat(picks.remainingPicks(group, user, 2026, 1)).isEqualTo(9);
        // The other group allows exactly one, and it is spent.
        assertThat(picks.remainingPicks(other, user, 2026, 1)).isZero();
        assertThatThrownBy(() -> picks.create(other, user, week.get(1).getId(), Selection.HOME))
                .isInstanceOf(WeeklyLimitReachedException.class);
    }

    /** A group with no maximum never runs out. */
    @Test
    void aGroupWithNoMaximumHasNoLimit() {
        UUID user = member("unlimited");
        List<Game> week = openGames(12);
        Group open = groups.save(new Group(user, TestGroups.settings("Open", Cadence.WEEKLY, null)));
        groupMembers.save(new GroupMember(open.getId(), user, GroupRole.OWNER));

        for (Game game : week) {
            picks.create(open, user, game.getId(), Selection.HOME);
        }

        assertThat(picks.remainingPicks(open, user, 2026, 1)).isNull();
        assertThat(pickRepository.findForUserWeek(open.getId(), user, 2026, 1)).hasSize(12);
    }

    /** A market the group does not play cannot be picked, even with a line posted. */
    @Test
    void aDisabledMarketCannotBePicked() {
        UUID user = member("spread-only");
        Game game = openGames(1).get(0);

        Group spreadOnly = groups.save(new Group(user,
                TestGroups.settings("Spread only", Cadence.WEEKLY, 10, true, false)));
        groupMembers.save(new GroupMember(spreadOnly.getId(), user, GroupRole.OWNER));

        assertThatThrownBy(() -> picks.create(spreadOnly, user, game.getId(), Selection.OVER))
                .isInstanceOf(PickExceptions.InvalidPickException.class)
                .hasMessageContaining("does not play the over/under");

        // The market it does play is unaffected.
        assertThat(picks.create(spreadOnly, user, game.getId(), Selection.HOME)).isNotNull();
    }

    /**
     * A weekly countdown is meaningless for a daily group, and reporting one
     * anyway was worse than reporting nothing: the counter rows are keyed by
     * date, so a weekly lookup missed them entirely and claimed a full
     * allowance while picking went on rejecting the second pick of the day.
     */
    @Test
    void aDailyGroupHasNoWeeklyRemainingCount() {
        UUID user = member("daily-budget");
        Group daily = groups.save(new Group(user, TestGroups.settings("Daily", Cadence.DAILY, 1)));
        groupMembers.save(new GroupMember(daily.getId(), user, GroupRole.OWNER));

        Game saturday = game(2101L, Instant.now().plus(3, ChronoUnit.DAYS));
        picks.create(daily, user, saturday.getId(), Selection.HOME);

        assertThat(picks.remainingPicks(daily, user, 2026, 1)).isNull();

        // The weekly group beside it still reports a real number.
        assertThat(picks.remainingPicks(group, member("weekly-budget"), 2026, 1)).isEqualTo(10);
    }

    /** A daily group counts its allowance per game day, not per week. */
    @Test
    void aDailyGroupCountsByDay() {
        UUID user = member("daily");
        Group daily = groups.save(new Group(user, TestGroups.settings("Daily", Cadence.DAILY, 1)));
        groupMembers.save(new GroupMember(daily.getId(), user, GroupRole.OWNER));

        // Two games in the same week but three days apart.
        Game saturday = game(2001L, Instant.now().plus(3, ChronoUnit.DAYS));
        Game tuesday = game(2002L, Instant.now().plus(6, ChronoUnit.DAYS));

        picks.create(daily, user, saturday.getId(), Selection.HOME);

        // The cap of one is spent for Saturday, but the later day is untouched.
        assertThatThrownBy(() -> picks.create(daily, user, saturday.getId(), Selection.OVER))
                .isInstanceOf(WeeklyLimitReachedException.class);
        assertThat(picks.create(daily, user, tuesday.getId(), Selection.HOME)).isNotNull();
    }

    // ------------------------------------------------------------- fixtures

    private UUID member(String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name, name));
        if (group != null) {
            groupMembers.save(new GroupMember(group.getId(), id, GroupRole.MEMBER));
        }
        return id;
    }

    private List<Game> openGames(int count) {
        List<Game> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(game(1000L + i, Instant.now().plus(3, ChronoUnit.DAYS)));
        }
        return created;
    }

    private Game game(long id, Instant kickoff) {
        return game(id, 1, kickoff);
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
