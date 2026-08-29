package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary behaviour of the 30-minute lock. */
class PickWindowTest {

    private static final Instant KICKOFF = Instant.parse("2026-09-05T19:00:00Z");
    private static final Instant LOCKS_AT = Instant.parse("2026-09-05T18:30:00Z");

    private PickWindow window;

    @BeforeEach
    void setUp() {
        window = new PickWindow(new AppProperties());
    }

    @Test
    void openUntilExactlyThirtyMinutesBeforeKickoff() {
        Game game = game();

        assertThat(window.isOpen(game, LOCKS_AT.minusSeconds(1))).isTrue();
        // At exactly the lock instant the window is already shut - "up to 30
        // minutes prior" is exclusive of the boundary.
        assertThat(window.isOpen(game, LOCKS_AT)).isFalse();
        assertThat(window.isOpen(game, LOCKS_AT.plusSeconds(1))).isFalse();
    }

    @Test
    void closedWithoutAPostedLine() {
        Game game = game();
        game.setHomeSpread(null);

        // The window itself is still open - there is simply nothing to pick
        // against in this market.
        assertThat(window.isOpen(game, KICKOFF.minusSeconds(86_400))).isTrue();
        assertThat(window.isOpen(game, Market.SPREAD, KICKOFF.minusSeconds(86_400))).isFalse();
    }

    @Test
    void closedWhenKickoffTimeIsUndecided() {
        // A 30-minute lock is meaningless without a real kickoff time.
        Game game = game();
        game.setStartTimeTbd(true);

        assertThat(window.isOpen(game, KICKOFF.minusSeconds(86_400))).isFalse();
    }

    @Test
    void closedOnceTheGameIsUnderway() {
        Game game = game();
        game.setStatus(GameStatus.IN_PROGRESS);

        assertThat(window.isOpen(game, KICKOFF.minusSeconds(86_400))).isFalse();
    }

    @Test
    void picksStayHiddenUntilKickoff() {
        Game game = game();

        assertThat(window.isRevealed(game, LOCKS_AT)).isFalse();
        assertThat(window.isRevealed(game, KICKOFF.minusSeconds(1))).isFalse();
        assertThat(window.isRevealed(game, KICKOFF)).isTrue();
    }

    /**
     * Spreads are from the home perspective, so "better" runs in opposite
     * directions for the two sides. Getting this backwards would offer a
     * button that makes a pick worse.
     */
    @Test
    void homePicksImproveAsTheSpreadRises() {
        Game game = game();
        Pick pick = pick(Selection.HOME, "-7.5");

        // Home now only has to win by 3 instead of 7.5.
        game.setHomeSpread(new BigDecimal("-3.0"));
        assertThat(window.isLineImproved(pick, game)).isTrue();

        // Home now has to win by 10 - worse.
        game.setHomeSpread(new BigDecimal("-10.0"));
        assertThat(window.isLineImproved(pick, game)).isFalse();

        game.setHomeSpread(new BigDecimal("-7.5"));
        assertThat(window.isLineImproved(pick, game)).isFalse();
    }

    @Test
    void awayPicksImproveAsTheSpreadFalls() {
        Game game = game();
        Pick pick = pick(Selection.AWAY, "-7.5");

        // Away now receives 10 points instead of 7.5.
        game.setHomeSpread(new BigDecimal("-10.0"));
        assertThat(window.isLineImproved(pick, game)).isTrue();

        // Away now receives only 3 - worse.
        game.setHomeSpread(new BigDecimal("-3.0"));
        assertThat(window.isLineImproved(pick, game)).isFalse();
    }

    @Test
    void aPulledLineIsNeverAnImprovement() {
        Game game = game();
        game.setHomeSpread(null);

        assertThat(window.isLineImproved(pick(Selection.HOME, "-7.5"), game)).isFalse();
        assertThat(window.isLineImproved(pick(Selection.AWAY, "-7.5"), game)).isFalse();
    }

    /**
     * Totals run the opposite way to spreads: an OVER wants a lower number,
     * an UNDER a higher one. Inverting this offers a button that quietly
     * makes a pick worse.
     */
    @Test
    void overPicksImproveAsTheTotalFalls() {
        Game game = game();
        Pick pick = pick(Selection.OVER, "45.5");

        // Only 42.5 points needed now instead of 45.5.
        game.setOverUnder(new BigDecimal("42.5"));
        assertThat(window.isLineImproved(pick, game)).isTrue();

        game.setOverUnder(new BigDecimal("48.5"));
        assertThat(window.isLineImproved(pick, game)).isFalse();

        game.setOverUnder(new BigDecimal("45.5"));
        assertThat(window.isLineImproved(pick, game)).isFalse();
    }

    @Test
    void underPicksImproveAsTheTotalRises() {
        Game game = game();
        Pick pick = pick(Selection.UNDER, "45.5");

        // More room before the total is reached.
        game.setOverUnder(new BigDecimal("48.5"));
        assertThat(window.isLineImproved(pick, game)).isTrue();

        game.setOverUnder(new BigDecimal("42.5"));
        assertThat(window.isLineImproved(pick, game)).isFalse();
    }

    @Test
    void aTotalPickReadsTheTotalNotTheSpread() {
        Game game = game();
        // The spread moves in a direction that would flatter a HOME pick, but
        // a total pick must ignore it entirely.
        game.setHomeSpread(new BigDecimal("-1.0"));
        game.setOverUnder(null);

        assertThat(window.isLineImproved(pick(Selection.OVER, "45.5"), game)).isFalse();
        assertThat(window.isLineImproved(pick(Selection.UNDER, "45.5"), game)).isFalse();
    }

    /** A game can carry one market's number without the other's. */
    @Test
    void marketAvailabilityIsIndependent() {
        Game game = game();
        game.setOverUnder(null);

        assertThat(window.isOpen(game, Market.SPREAD, LOCKS_AT.minusSeconds(1))).isTrue();
        assertThat(window.isOpen(game, Market.TOTAL, LOCKS_AT.minusSeconds(1))).isFalse();

        game.setHomeSpread(null);
        game.setOverUnder(new BigDecimal("45.5"));

        assertThat(window.isOpen(game, Market.SPREAD, LOCKS_AT.minusSeconds(1))).isFalse();
        assertThat(window.isOpen(game, Market.TOTAL, LOCKS_AT.minusSeconds(1))).isTrue();
    }

    @Test
    void theLockAppliesToBothMarketsAlike() {
        Game game = game();
        game.setOverUnder(new BigDecimal("45.5"));

        // Kickoff is a property of the game, not of a market.
        assertThat(window.isOpen(game, Market.SPREAD, LOCKS_AT)).isFalse();
        assertThat(window.isOpen(game, Market.TOTAL, LOCKS_AT)).isFalse();
    }

    private Pick pick(Selection selection, String lockedLine) {
        Pick pick = new Pick();
        pick.setGameId(1L);
        pick.setSelection(selection);
        pick.setLockedLine(new BigDecimal(lockedLine));
        return pick;
    }

    private Game game() {
        Game game = new Game();
        game.setId(1L);
        game.setSeason(2026);
        game.setWeek(2);
        game.setHomeTeam("Iowa State");
        game.setAwayTeam("Kansas");
        game.setKickoff(KICKOFF);
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setOverUnder(new BigDecimal("45.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return game;
    }
}
