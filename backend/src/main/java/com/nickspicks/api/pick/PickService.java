package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.pick.PickExceptions.InvalidPickException;
import com.nickspicks.api.pick.PickExceptions.LineMovedException;
import com.nickspicks.api.pick.PickExceptions.PickWindowClosedException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enforces the two rules that make this a pick'em rather than a form:
 * at most ten picks a week, and nothing changes inside 30 minutes of kickoff.
 */
@Service
public class PickService {

    private final PickRepository picks;
    private final WeeklyEntryRepository entries;
    private final GameRepository games;
    private final PickWindow window;
    private final AppProperties properties;
    private final PickAuditRepository audit;

    public PickService(PickRepository picks, WeeklyEntryRepository entries, GameRepository games,
                       PickWindow window, AppProperties properties, PickAuditRepository audit) {
        this.picks = picks;
        this.entries = entries;
        this.games = games;
        this.window = window;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Pick> findForUserWeek(UUID userId, int season, int week) {
        return picks.findForUserWeek(userId, season, week);
    }

    @Transactional(readOnly = true)
    public int remainingPicks(UUID userId, int season, int week) {
        int used = entries.findById(new WeeklyEntry.Key(userId, season, week))
                .map(WeeklyEntry::getPickCount)
                .orElse(0);
        return Math.max(0, maxPicks() - used);
    }

    /**
     * Another member's card for a week, with anything not yet kicked off
     * stripped out. Filtering happens here rather than in the UI - hiding it
     * client-side would still ship the data.
     */
    @Transactional(readOnly = true)
    public List<Pick> findRevealedForUserWeek(UUID userId, int season, int week) {
        Instant now = Instant.now();
        return picks.findForUserWeek(userId, season, week).stream()
                .filter(pick -> games.findById(pick.getGameId())
                        .map(game -> window.isRevealed(game, now))
                        .orElse(false))
                .toList();
    }

    @Transactional
    public Pick create(UUID userId, Long gameId, Selection selection) {
        return create(userId, gameId, selection, null);
    }

    /**
     * @param expectedLine the line the member was looking at, or null if the
     *                       caller does not care. Supplying it turns a stale
     *                       page into a visible conflict instead of a pick
     *                       silently made at a number they never saw.
     */
    @Transactional
    public Pick create(UUID userId, Long gameId, Selection selection, BigDecimal expectedLine) {
        Game game = requireGame(gameId);
        Market market = selection.market();

        requireOpen(game, market);
        requireCurrentLine(game, market, expectedLine);

        WeeklyEntry entry = lockEntry(userId, game);

        // One shared allowance across both markets - a member spends their ten
        // picks wherever they like.
        if (entry.getPickCount() >= maxPicks()) {
            throw new WeeklyLimitReachedException(
                    "You already have %d picks for week %d".formatted(maxPicks(), game.getWeek()));
        }
        if (picks.findByUserIdAndGameIdAndMarket(userId, gameId, market).isPresent()) {
            throw new InvalidPickException(market == Market.TOTAL
                    ? "You already picked the total on this game"
                    : "You already picked the spread on this game");
        }

        Pick pick = new Pick();
        pick.setUserId(userId);
        pick.setGameId(gameId);
        pick.setSelection(selection);
        // Lock the line as it stands now; later movement will not affect it.
        pick.setLockedLine(window.line(game, market));

        entry.setPickCount(entry.getPickCount() + 1);
        entries.save(entry);

        try {
            Pick saved = picks.save(pick);
            audit.save(PickAudit.created(saved));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // The unique (user_id, game_id) index caught a duplicate that
            // slipped past the check above.
            throw new InvalidPickException("You already picked this game");
        }
    }

    @Transactional
    public Pick update(UUID userId, UUID pickId, Selection selection) {
        return update(userId, pickId, selection, null);
    }

    @Transactional
    public Pick update(UUID userId, UUID pickId, Selection selection, BigDecimal expectedLine) {
        Pick pick = requireOwnedPick(userId, pickId);
        Game game = requireGame(pick.getGameId());
        Market market = pick.getMarket();

        // Switching markets is a different pick, not an edit - it would change
        // the row's identity under the (user, game, market) key. Cancel and
        // create instead.
        if (selection.market() != market) {
            throw new InvalidPickException(
                    "Cancel this pick and make a new one to switch between the spread and the total");
        }

        requireOpen(game, market);
        requireCurrentLine(game, market, expectedLine);

        // Take the lock even though the count is unchanged, so an edit cannot
        // interleave with a concurrent create on the same week.
        lockEntry(userId, game);

        Selection previousSelection = pick.getSelection();
        BigDecimal previousLine = pick.getLockedLine();

        pick.setSelection(selection);
        // Editing re-locks the current line - the member is committing to
        // today's number, not the one they saw last week.
        pick.setLockedLine(window.line(game, market));
        pick.setUpdatedAt(Instant.now());

        Pick saved = picks.save(pick);
        audit.save(PickAudit.updated(saved, previousSelection, previousLine));
        return saved;
    }

    /**
     * Moves a pick onto the game's current line without changing sides.
     *
     * <p>Only allowed when the line actually improved for the side taken -
     * the button exists to take a freebie, and letting it re-lock a worse
     * number would be a trap. Still subject to the 30-minute window.
     */
    @Transactional
    public Pick relock(UUID userId, UUID pickId) {
        Pick pick = requireOwnedPick(userId, pickId);
        Game game = requireGame(pick.getGameId());
        requireOpen(game, pick.getMarket());

        if (!window.isLineImproved(pick, game)) {
            throw new InvalidPickException(
                    "The current line is not better than the one you already have");
        }

        lockEntry(userId, game);

        Selection selection = pick.getSelection();
        BigDecimal previousLine = pick.getLockedLine();

        pick.setLockedLine(window.line(game, pick.getMarket()));
        pick.setUpdatedAt(Instant.now());

        Pick saved = picks.save(pick);
        audit.save(PickAudit.updated(saved, selection, previousLine));
        return saved;
    }

    /** Whether this pick could be re-locked at a better number right now. */
    @Transactional(readOnly = true)
    public boolean isLineImproved(Pick pick) {
        return games.findById(pick.getGameId())
                .map(game -> window.isOpen(game) && window.isLineImproved(pick, game))
                .orElse(false);
    }

    /** @return the pick's game, so the caller can hand back updated card state without a second read. */
    @Transactional
    public Game delete(UUID userId, UUID pickId) {
        Pick pick = requireOwnedPick(userId, pickId);
        Game game = requireGame(pick.getGameId());
        requireOpen(game, pick.getMarket());

        WeeklyEntry entry = lockEntry(userId, game);
        entry.setPickCount(Math.max(0, entry.getPickCount() - 1));
        entries.save(entry);

        // Audit before delete so the row still has its final state to record.
        audit.save(PickAudit.cancelled(pick));
        picks.delete(pick);
        return game;
    }

    @Transactional(readOnly = true)
    public List<Pick> findForUserGame(UUID userId, Long gameId) {
        return picks.findAllByUserIdAndGameId(userId, gameId);
    }

    private WeeklyEntry lockEntry(UUID userId, Game game) {
        return entries.findAndLock(userId, game.getSeason(), game.getWeek())
                .orElseGet(() -> entries.save(
                        new WeeklyEntry(userId, game.getSeason(), game.getWeek())));
    }

    /**
     * Rejects a pick placed against a line that has since moved.
     *
     * <p>Without this, a tab left open overnight commits the member to
     * whatever the number is now - which may be several points away from what
     * they were looking at when they clicked. Skipped when the caller sends
     * no expectation, so older clients and server-side callers still work.
     */
    private void requireCurrentLine(Game game, Market market, BigDecimal expectedLine) {
        BigDecimal current = window.line(game, market);

        if (expectedLine == null || current == null) {
            return;
        }
        // compareTo, not equals: -7.5 and -7.50 are the same line.
        if (expectedLine.compareTo(current) != 0) {
            throw new LineMovedException(
                    "The line moved to %s while this page was open".formatted(current), current);
        }
    }

    private void requireOpen(Game game, Market market) {
        if (!window.isOpen(game)) {
            throw new PickWindowClosedException(
                    "Picks for %s at %s closed %d minutes before kickoff"
                            .formatted(game.getAwayTeam(), game.getHomeTeam(),
                                    properties.getPickem().getLockLeadMinutes()));
        }
        // Open, but this particular market has no number posted.
        if (!window.hasLine(game, market)) {
            throw new InvalidPickException(market == Market.TOTAL
                    ? "No total is posted for this game yet"
                    : "No spread is posted for this game yet");
        }
    }

    private Game requireGame(Long gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));
    }

    private Pick requireOwnedPick(UUID userId, UUID pickId) {
        Pick pick = picks.findById(pickId)
                .orElseThrow(() -> new NotFoundException("Pick %s not found".formatted(pickId)));
        if (!pick.getUserId().equals(userId)) {
            // Same response as a missing pick - do not confirm that someone
            // else's pick id exists.
            throw new NotFoundException("Pick %s not found".formatted(pickId));
        }
        return pick;
    }

    private int maxPicks() {
        return properties.getPickem().getMaxPicksPerWeek();
    }
}
