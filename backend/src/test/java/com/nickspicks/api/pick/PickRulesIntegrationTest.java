package com.nickspicks.api.pick;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.pick.PickExceptions.PickWindowClosedException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
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
    private WeeklyEntryRepository entries;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Override
    protected void cleanUp() {
        pickRepository.deleteAll();
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void allowsTenPicksAndRejectsTheEleventh() {
        UUID user = member("ten-picks");
        List<Game> week = openGames(11);

        for (int i = 0; i < 10; i++) {
            picks.create(user, week.get(i).getId(), Selection.HOME);
        }

        assertThat(picks.remainingPicks(user, 2026, 1)).isZero();
        assertThatThrownBy(() -> picks.create(user, week.get(10).getId(), Selection.AWAY))
                .isInstanceOf(WeeklyLimitReachedException.class);
        assertThat(pickRepository.findForUserWeek(user, 2026, 1)).hasSize(10);
    }

    /**
     * The reason weekly_entry exists. Without the row lock, both threads read
     * a count of nine and both insert, leaving the member with eleven picks.
     */
    @Test
    void twoConcurrentTenthPicksLeaveExactlyTenPicks() throws Exception {
        UUID user = member("racer");
        List<Game> week = openGames(12);

        for (int i = 0; i < 9; i++) {
            picks.create(user, week.get(i).getId(), Selection.HOME);
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
                        picks.create(user, gameId, Selection.HOME);
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
        assertThat(pickRepository.findForUserWeek(user, 2026, 1)).hasSize(10);
        assertThat(entries.findById(new WeeklyEntry.Key(user, 2026, 1)))
                .get()
                .extracting(WeeklyEntry::getPickCount)
                .isEqualTo(10);
    }

    @Test
    void refusesToCreateEditOrCancelInsideTheLockWindow() {
        UUID user = member("late");
        Game open = openGames(1).get(0);
        Pick existing = picks.create(user, open.getId(), Selection.HOME);

        // Slide kickoff to 29 minutes away - now inside the 30-minute lock.
        open.setKickoff(Instant.now().plus(29, ChronoUnit.MINUTES));
        games.save(open);

        Game other = game(99L, Instant.now().plus(29, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> picks.create(user, other.getId(), Selection.HOME))
                .isInstanceOf(PickWindowClosedException.class);
        assertThatThrownBy(() -> picks.update(user, existing.getId(), Selection.AWAY))
                .isInstanceOf(PickWindowClosedException.class);
        assertThatThrownBy(() -> picks.delete(user, existing.getId()))
                .isInstanceOf(PickWindowClosedException.class);
    }

    @Test
    void cancellingFreesTheSlot() {
        UUID user = member("canceller");
        List<Game> week = openGames(2);

        Pick first = picks.create(user, week.get(0).getId(), Selection.HOME);
        assertThat(picks.remainingPicks(user, 2026, 1)).isEqualTo(9);

        picks.delete(user, first.getId());
        assertThat(picks.remainingPicks(user, 2026, 1)).isEqualTo(10);
    }

    @Test
    void editingRelocksTheCurrentLine() {
        UUID user = member("editor");
        Game game = openGames(1).get(0);

        Pick pick = picks.create(user, game.getId(), Selection.HOME);
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");

        game.setHomeSpread(new BigDecimal("-3.0"));
        games.save(game);

        Pick edited = picks.update(user, pick.getId(), Selection.AWAY);
        assertThat(edited.getLockedLine()).isEqualByComparingTo("-3.0");
    }

    @Test
    void lineMovementDoesNotDisturbAnExistingPick() {
        UUID user = member("early-bird");
        Game game = openGames(1).get(0);

        Pick pick = picks.create(user, game.getId(), Selection.HOME);

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

        Pick pick = picks.create(user, game.getId(), Selection.HOME);
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");

        // Line moves against the home side - no re-lock on offer.
        game.setHomeSpread(new BigDecimal("-10.0"));
        games.save(game);
        assertThatThrownBy(() -> picks.relock(user, pick.getId()))
                .isInstanceOf(PickExceptions.InvalidPickException.class);
        assertThat(pickRepository.findById(pick.getId()))
                .get().extracting(Pick::getLockedLine).isEqualTo(new BigDecimal("-7.5"));

        // Line moves in their favour - re-lock, same side, better number.
        game.setHomeSpread(new BigDecimal("-2.5"));
        games.save(game);
        Pick relocked = picks.relock(user, pick.getId());
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
                picks.create(user, game.getId(), Selection.HOME, new BigDecimal("-7.5")))
                .isInstanceOf(PickExceptions.LineMovedException.class);
        assertThat(pickRepository.findForUserWeek(user, 2026, 1)).isEmpty();

        // Re-sent with the number actually on screen, it goes through.
        Pick pick = picks.create(user, game.getId(), Selection.HOME, new BigDecimal("-10.0"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-10.0");
    }

    @Test
    void trailingZeroesAreTheSameLine() {
        UUID user = member("precision");
        Game game = openGames(1).get(0);

        // -7.5 and -7.50 are the same number; only a real move should conflict.
        Pick pick = picks.create(user, game.getId(), Selection.HOME, new BigDecimal("-7.50"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("-7.5");
    }

    @Test
    void relockIsRefusedOnceTheWindowCloses() {
        UUID user = member("latecomer");
        Game game = openGames(1).get(0);
        Pick pick = picks.create(user, game.getId(), Selection.HOME);

        game.setHomeSpread(new BigDecimal("-1.0"));
        game.setKickoff(Instant.now().plus(20, ChronoUnit.MINUTES));
        games.save(game);

        assertThatThrownBy(() -> picks.relock(user, pick.getId()))
                .isInstanceOf(PickWindowClosedException.class);
    }

    // ------------------------------------------------------ over/under

    @Test
    void bothMarketsCanBePickedOnTheSameGame() {
        UUID user = member("two-markets");
        Game game = openGames(1).get(0);

        Pick spread = picks.create(user, game.getId(), Selection.HOME);
        Pick total = picks.create(user, game.getId(), Selection.OVER);

        assertThat(spread.getMarket()).isEqualTo(Market.SPREAD);
        assertThat(spread.getLockedLine()).isEqualByComparingTo("-7.5");
        assertThat(total.getMarket()).isEqualTo(Market.TOTAL);
        // The total pick locks the total, not the spread.
        assertThat(total.getLockedLine()).isEqualByComparingTo("45.5");

        // Both draw on the one shared allowance.
        assertThat(picks.remainingPicks(user, 2026, 1)).isEqualTo(8);
    }

    @Test
    void asecondPickOnTheSameMarketIsRejected() {
        UUID user = member("doubler");
        Game game = openGames(1).get(0);

        picks.create(user, game.getId(), Selection.OVER);

        assertThatThrownBy(() -> picks.create(user, game.getId(), Selection.UNDER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);
        assertThat(pickRepository.findForUserWeek(user, 2026, 1)).hasSize(1);
    }

    /** The cap is one pool, however the picks are split between markets. */
    @Test
    void theWeeklyCapCountsBothMarketsTogether() {
        UUID user = member("mixer");
        List<Game> week = openGames(6);

        for (int i = 0; i < 5; i++) {
            picks.create(user, week.get(i).getId(), Selection.HOME);
        }
        for (int i = 0; i < 5; i++) {
            picks.create(user, week.get(i).getId(), Selection.OVER);
        }

        assertThat(picks.remainingPicks(user, 2026, 1)).isZero();
        assertThatThrownBy(() -> picks.create(user, week.get(5).getId(), Selection.HOME))
                .isInstanceOf(WeeklyLimitReachedException.class);
    }

    @Test
    void aTotalPickIsCheckedAgainstTheTotalNotTheSpread() {
        UUID user = member("careful");
        Game game = openGames(1).get(0);

        // Sending the spread as the expected line for a total pick must not
        // be mistaken for a match.
        assertThatThrownBy(() ->
                picks.create(user, game.getId(), Selection.OVER, new BigDecimal("-7.5")))
                .isInstanceOf(PickExceptions.LineMovedException.class);

        Pick pick = picks.create(user, game.getId(), Selection.OVER, new BigDecimal("45.5"));
        assertThat(pick.getLockedLine()).isEqualByComparingTo("45.5");
    }

    @Test
    void switchingMarketsOnAnExistingPickIsRejected() {
        UUID user = member("switcher");
        Game game = openGames(1).get(0);
        Pick pick = picks.create(user, game.getId(), Selection.HOME);

        assertThatThrownBy(() -> picks.update(user, pick.getId(), Selection.OVER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);

        // Switching within the market is still fine.
        assertThat(picks.update(user, pick.getId(), Selection.AWAY).getSelection())
                .isEqualTo(Selection.AWAY);
    }

    @Test
    void aGameWithNoPostedTotalCannotBeTotalPicked() {
        UUID user = member("no-total");
        Game game = openGames(1).get(0);
        game.setOverUnder(null);
        games.save(game);

        assertThatThrownBy(() -> picks.create(user, game.getId(), Selection.OVER))
                .isInstanceOf(PickExceptions.InvalidPickException.class);

        // The spread on the same game is unaffected.
        assertThat(picks.create(user, game.getId(), Selection.HOME)).isNotNull();
    }

    @Test
    void otherMembersPicksAreHiddenUntilKickoff() {
        UUID user = member("secretive");
        Game upcoming = openGames(1).get(0);
        picks.create(user, upcoming.getId(), Selection.HOME);

        assertThat(picks.findRevealedForUserWeek(user, 2026, 1)).isEmpty();

        // Once it kicks off, the pick becomes visible.
        upcoming.setKickoff(Instant.now().minus(5, ChronoUnit.MINUTES));
        games.save(upcoming);

        assertThat(picks.findRevealedForUserWeek(user, 2026, 1)).hasSize(1);
    }

    // ------------------------------------------------------------- fixtures

    private UUID member(String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name));
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
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(1);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(kickoff);
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setOverUnder(new BigDecimal("45.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }
}
