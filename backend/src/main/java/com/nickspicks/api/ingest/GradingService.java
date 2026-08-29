package com.nickspicks.api.ingest;

import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.pick.Pick;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickResult;
import com.nickspicks.api.pick.Selection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Settles picks against the spread.
 *
 * <p>The spread is always from the home team's perspective, matching CFBD:
 * {@code -7.5} means the home team is favored by 7.5. Add it to the home
 * score to get the adjusted result, then compare.
 *
 * <p>Grading uses the pick's own {@code lockedLine}, never the game's
 * current line, so two members who picked the same side at different times can
 * legitimately get different results.
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final PickRepository picks;

    public GradingService(PickRepository picks) {
        this.picks = picks;
    }

    /**
     * Pure function - the whole point of grading lives here and is unit
     * tested as a truth table.
     *
     * <p>Both markets reduce to the same three outcomes, so everything
     * downstream of this - standings, the leaderboard, the audit - is
     * indifferent to which one was played.
     */
    public PickResult grade(Selection selection, BigDecimal lockedLine,
                            int homeScore, int awayScore) {
        int comparison = switch (selection.market()) {
            case SPREAD -> gradeSpread(selection, lockedLine, homeScore, awayScore);
            case TOTAL -> gradeTotal(selection, lockedLine, homeScore, awayScore);
        };

        if (comparison > 0) {
            return PickResult.WIN;
        }
        if (comparison < 0) {
            return PickResult.LOSS;
        }
        // Only possible on a whole number; a half-point line cannot push.
        return PickResult.PUSH;
    }

    /** The spread is from the home perspective: add it to the home score. */
    private int gradeSpread(Selection selection, BigDecimal lockedLine,
                            int homeScore, int awayScore) {
        BigDecimal adjustedHome = BigDecimal.valueOf(homeScore).add(lockedLine);
        BigDecimal away = BigDecimal.valueOf(awayScore);

        return selection == Selection.HOME
                ? adjustedHome.compareTo(away)
                : away.compareTo(adjustedHome);
    }

    /** Totals are played against the combined score. */
    private int gradeTotal(Selection selection, BigDecimal lockedLine,
                           int homeScore, int awayScore) {
        BigDecimal total = BigDecimal.valueOf(homeScore + awayScore);

        return selection == Selection.OVER
                ? total.compareTo(lockedLine)
                : lockedLine.compareTo(total);
    }

    /** Grades every pending pick on a finished game. Returns how many. */
    @Transactional
    public int gradeGame(Game game) {
        List<Pick> pending = picks.findAllByGameId(game.getId()).stream()
                .filter(pick -> pick.getResult() == PickResult.PENDING)
                .toList();

        if (pending.isEmpty()) {
            return 0;
        }

        if (game.getStatus() == GameStatus.CANCELED) {
            // Void rather than settle - a canceled game is not a loss for
            // anyone, and voided picks are excluded from the standings.
            pending.forEach(pick -> {
                pick.setResult(PickResult.VOID);
                pick.setGradedAt(Instant.now());
                pick.setUpdatedAt(Instant.now());
            });
            picks.saveAll(pending);
            log.info("Voided {} picks on canceled game {}", pending.size(), game.getId());
            return pending.size();
        }

        if (game.getStatus() != GameStatus.FINAL
                || game.getHomeScore() == null || game.getAwayScore() == null) {
            return 0;
        }

        for (Pick pick : pending) {
            pick.setResult(grade(pick.getSelection(), pick.getLockedLine(),
                    game.getHomeScore(), game.getAwayScore()));
            pick.setGradedAt(Instant.now());
            pick.setUpdatedAt(Instant.now());
        }
        picks.saveAll(pending);

        log.info("Graded {} picks on game {} ({} {} - {} {})", pending.size(), game.getId(),
                game.getAwayTeam(), game.getAwayScore(), game.getHomeTeam(), game.getHomeScore());
        return pending.size();
    }
}
