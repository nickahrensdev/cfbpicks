package com.nickspicks.api.ingest;

import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Minute-by-minute score/clock updates and grading, driven by an external
 * caller (Supabase pg_cron, via {@code CronController}) rather than this
 * app's own {@code @Scheduled} jobs.
 *
 * <p>ESPN's site API has no call limit, unlike CFBD's 5,000/month, so it is
 * safe to poll every minute for the score/clock/quarter update this class
 * writes and for near-real-time grading. The existing 15-minute
 * {@code IngestScheduler}/CFBD path and the manual admin
 * {@code /ingest/scores} endpoint are both untouched by this - either can
 * still be the one that first reports a game final, and
 * {@link GradingService#gradeGame} is idempotent either way.
 */
@Service
public class EspnScoreIngestService {

    private static final Logger log = LoggerFactory.getLogger(EspnScoreIngestService.class);

    private final GameRepository games;
    private final LiveScoreService liveScores;
    private final GradingService grading;

    public EspnScoreIngestService(GameRepository games, LiveScoreService liveScores,
                                  GradingService grading) {
        this.games = games;
        this.liveScores = liveScores;
        this.grading = grading;
    }

    public record Result(int gamesUpdated, int gamesGraded) {
    }

    @Transactional
    public Result pollAndGrade() {
        Instant now = Instant.now();
        // Same "worth touching" window ingestScores/hasLiveGames already use -
        // kicked off in the last 6 hours, not already settled or canceled.
        List<Game> candidates = games.findPotentiallyLive(now, now.minusSeconds(6 * 3600));
        if (candidates.isEmpty()) {
            return new Result(0, 0);
        }

        Map<Long, LiveScoreService.LiveGame> live = liveScores.current();
        int updated = 0;
        int graded = 0;

        for (Game game : candidates) {
            LiveScoreService.LiveGame espnGame = live.get(game.getId());
            if (espnGame == null) {
                continue;
            }

            GameStatus before = game.getStatus();
            applyLiveState(game, espnGame);
            games.save(game);
            updated++;

            if (game.getStatus() == GameStatus.FINAL && before != GameStatus.FINAL) {
                graded += grading.gradeGame(game);
            }
        }

        if (updated > 0) {
            log.info("ESPN poll: updated {} games, graded {}", updated, graded);
        }
        return new Result(updated, graded);
    }

    /**
     * home_score/away_score are reused as-is - "the score" is one fact
     * regardless of which provider reported it, and the rest of the app
     * already reads those two columns everywhere. espn_period/espn_clock are
     * display-only and cleared once the game is final.
     */
    private void applyLiveState(Game game, LiveScoreService.LiveGame espnGame) {
        if (espnGame.homeScore() != null) {
            game.setHomeScore(espnGame.homeScore());
        }
        if (espnGame.awayScore() != null) {
            game.setAwayScore(espnGame.awayScore());
        }

        boolean finished = "post".equals(espnGame.state());
        if (finished) {
            game.setStatus(GameStatus.FINAL);
            game.setEspnPeriod(null);
            game.setEspnClock(null);
        } else {
            if (espnGame.inProgress()) {
                game.setStatus(GameStatus.IN_PROGRESS);
            }
            game.setEspnPeriod(espnGame.period());
            game.setEspnClock(espnGame.clock());
        }

        game.setUpdatedAt(Instant.now());
    }
}
