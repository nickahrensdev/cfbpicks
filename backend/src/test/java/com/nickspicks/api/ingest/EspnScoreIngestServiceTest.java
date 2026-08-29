package com.nickspicks.api.ingest;

import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one thing this service must get right: grading fires exactly on the
 * not-FINAL -> FINAL edge, never on every poll of an already-final game -
 * {@link GradingService#gradeGame} is idempotent too, but a service that
 * called it on every tick regardless would still be doing needless work
 * every minute, forever, for every finished game of the season.
 */
class EspnScoreIngestServiceTest {

    private GameRepository games;
    private LiveScoreService liveScores;
    private GradingService grading;
    private EspnScoreIngestService service;

    private void setUp() {
        games = mock(GameRepository.class);
        liveScores = mock(LiveScoreService.class);
        grading = mock(GradingService.class);
        service = new EspnScoreIngestService(games, liveScores, grading);
    }

    @Test
    void doesNothingWhenNoGameCouldPlausiblyBeLive() {
        setUp();
        when(games.findPotentiallyLive(any(), any())).thenReturn(List.of());

        EspnScoreIngestService.Result result = service.pollAndGrade();

        assertThat(result.gamesUpdated()).isZero();
        assertThat(result.gamesGraded()).isZero();
        verify(liveScores, never()).current();
    }

    @Test
    void updatesScoreAndClockWithoutGradingWhileAGameIsStillInProgress() {
        setUp();
        Game game = scheduledGame(100L);
        when(games.findPotentiallyLive(any(), any())).thenReturn(List.of(game));
        when(liveScores.current()).thenReturn(Map.of(100L, liveGame(100L, "in", 14, 10, 3, "4:21")));

        EspnScoreIngestService.Result result = service.pollAndGrade();

        assertThat(result.gamesUpdated()).isEqualTo(1);
        assertThat(result.gamesGraded()).isZero();
        assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(game.getHomeScore()).isEqualTo(14);
        assertThat(game.getAwayScore()).isEqualTo(10);
        assertThat(game.getEspnPeriod()).isEqualTo(3);
        assertThat(game.getEspnClock()).isEqualTo("4:21");
        verify(grading, never()).gradeGame(any());
    }

    @Test
    void gradesExactlyOnTheNotFinalToFinalTransition() {
        setUp();
        Game game = inProgressGame(101L);
        when(games.findPotentiallyLive(any(), any())).thenReturn(List.of(game));
        when(liveScores.current()).thenReturn(Map.of(101L, liveGame(101L, "post", 31, 20, null, null)));

        EspnScoreIngestService.Result result = service.pollAndGrade();

        assertThat(game.getStatus()).isEqualTo(GameStatus.FINAL);
        assertThat(game.getEspnPeriod()).isNull();
        assertThat(game.getEspnClock()).isNull();
        assertThat(result.gamesUpdated()).isEqualTo(1);
        verify(grading).gradeGame(game);
    }

    @Test
    void doesNotRegradeAGameThatWasAlreadyFinalBeforeThisPoll() {
        setUp();
        Game game = finalGame(102L);
        when(games.findPotentiallyLive(any(), any())).thenReturn(List.of(game));
        when(liveScores.current()).thenReturn(Map.of(102L, liveGame(102L, "post", 31, 20, null, null)));

        service.pollAndGrade();

        verify(grading, never()).gradeGame(any());
    }

    @Test
    void skipsAGameEspnHasNoDataForThisTick() {
        setUp();
        Game game = scheduledGame(103L);
        when(games.findPotentiallyLive(any(), any())).thenReturn(List.of(game));
        when(liveScores.current()).thenReturn(Map.of()); // ESPN outage or unlisted game

        EspnScoreIngestService.Result result = service.pollAndGrade();

        assertThat(result.gamesUpdated()).isZero();
        assertThat(game.getStatus()).isEqualTo(GameStatus.SCHEDULED);
    }

    private LiveScoreService.LiveGame liveGame(long id, String state, int homeScore, int awayScore,
                                               Integer period, String clock) {
        return new LiveScoreService.LiveGame(id, state, homeScore, awayScore, period,
                null, clock, null, null, null, false);
    }

    private Game scheduledGame(long id) {
        return baseGame(id, GameStatus.SCHEDULED);
    }

    private Game inProgressGame(long id) {
        return baseGame(id, GameStatus.IN_PROGRESS);
    }

    private Game finalGame(long id) {
        Game game = baseGame(id, GameStatus.FINAL);
        game.setHomeScore(31);
        game.setAwayScore(20);
        return game;
    }

    private Game baseGame(long id, GameStatus status) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(1);
        game.setHomeTeam("Home");
        game.setAwayTeam("Away");
        game.setKickoff(Instant.now().minus(1, ChronoUnit.HOURS));
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setStatus(status);
        return game;
    }
}
