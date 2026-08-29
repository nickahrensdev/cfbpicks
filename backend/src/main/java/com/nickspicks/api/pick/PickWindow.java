package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * When a game may be picked, edited or cancelled.
 *
 * <p>One place on purpose: the API and the UI countdown must agree, and the
 * server is the authority. A stale browser tab has to get a 409, not a silent
 * success.
 */
@Component
public class PickWindow {

    private final AppProperties properties;

    public PickWindow(AppProperties properties) {
        this.properties = properties;
    }

    public Duration lockLead() {
        return Duration.ofMinutes(properties.getPickem().getLockLeadMinutes());
    }

    /** The instant a game stops accepting changes. */
    public Instant locksAt(Game game) {
        return game.getKickoff().minus(lockLead());
    }

    /**
     * Whether the game is still accepting changes at all.
     *
     * <p>Timing only - the 30-minute lock is a property of kickoff, not of any
     * one market. Whether a particular market can be picked also needs
     * {@link #hasLine}.
     */
    public boolean isOpen(Game game) {
        return isOpen(game, Instant.now());
    }

    public boolean isOpen(Game game, Instant now) {
        return !game.isStartTimeTbd()                  // a lock needs a real kickoff time
                && game.getStatus() == GameStatus.SCHEDULED
                && now.isBefore(locksAt(game));
    }

    /** Whether a market can be picked: the window is open and it has a line. */
    public boolean isOpen(Game game, Market market, Instant now) {
        return isOpen(game, now) && hasLine(game, market);
    }

    public boolean isOpen(Game game, Market market) {
        return isOpen(game, market, Instant.now());
    }

    /**
     * A game can carry a spread and no total, or the reverse, so availability
     * is per market rather than one flag for the card.
     */
    public boolean hasLine(Game game, Market market) {
        return line(game, market) != null;
    }

    /** The game's current number for a market. */
    public BigDecimal line(Game game, Market market) {
        return market == Market.TOTAL ? game.getOverUnder() : game.getHomeSpread();
    }

    /** Whether this game's picks may be shown to members other than the owner. */
    public boolean isRevealed(Game game, Instant now) {
        return !now.isBefore(game.getKickoff());
    }

    /**
     * Whether the game's current line is strictly better for the side this
     * pick took, so the member could re-lock at a number that can only help.
     *
     * <p>The direction differs by selection, and the two markets run opposite
     * ways:
     *
     * <pre>
     *   HOME   improves as the spread rises   (fewer points to give)
     *   AWAY   improves as the spread falls   (more points received)
     *   OVER   improves as the total falls    (fewer points needed)
     *   UNDER  improves as the total rises    (more room)
     * </pre>
     *
     * <p>A sign error here hands out a "better line" button that quietly makes
     * a pick worse, so it is expressed once, here, and tested as a table.
     */
    public boolean isLineImproved(Pick pick, Game game) {
        BigDecimal current = line(game, pick.getMarket());
        BigDecimal locked = pick.getLockedLine();

        if (current == null || locked == null) {
            return false;
        }

        return switch (pick.getSelection()) {
            case HOME, UNDER -> current.compareTo(locked) > 0;
            case AWAY, OVER -> current.compareTo(locked) < 0;
        };
    }
}
