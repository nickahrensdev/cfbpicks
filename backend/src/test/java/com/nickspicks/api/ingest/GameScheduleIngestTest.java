package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The schedule load must never touch a score or a status.
 *
 * <p>It used to. Because it runs against CFBD's /games, which lags behind the
 * ESPN poller, a game ESPN had already settled could come back
 * {@code completed=false} with null points - and the load would null the score
 * and drop FINAL back to IN_PROGRESS. Nothing would have repaired it: the ESPN
 * poller only reconsiders games that kicked off within the last six hours, so a
 * finished game would have sat on the board as live until someone noticed and
 * pressed a button.
 *
 * <p>That was survivable while the load was manual and occasional. It is not
 * once it runs daily, which is why these are here.
 */
class GameScheduleIngestTest {

    private CfbdClient cfbd;
    private GameRepository games;
    private GameIngestService service;

    @BeforeEach
    void setUp() {
        cfbd = mock(CfbdClient.class);
        games = mock(GameRepository.class);
        GradingService grading = mock(GradingService.class);

        AppProperties properties = new AppProperties();
        properties.getPickem().setSeason(2026);

        service = new GameIngestService(cfbd, games, properties, grading);

        // saveAll is what the service writes through; hand back what it saved
        // so the assertions can look at the entities it actually mutated.
        when(games.saveAll(anyIterable())).thenAnswer(call -> call.getArgument(0));
    }

    /** The regression that made a daily schedule job unsafe. */
    @Test
    void leavesAFinishedGameFinishedWhenCfbdHasNotCaughtUp() {
        Game settled = existingGame(100L, GameStatus.FINAL, 31, 17);
        when(games.findAllById(anyIterable())).thenReturn(List.of(settled));
        when(cfbd.games(2026)).thenReturn(List.of(dto(100L, false, null, null)));

        service.ingestSchedule(2026);

        assertThat(settled.getStatus())
                .as("ESPN settled this game; a lagging CFBD payload must not undo that")
                .isEqualTo(GameStatus.FINAL);
        assertThat(settled.getHomeScore()).isEqualTo(31);
        assertThat(settled.getAwayScore()).isEqualTo(17);
    }

    /** The same, for a game still being played - the poller owns it too. */
    @Test
    void leavesALiveGamesScoreAlone() {
        Game live = existingGame(101L, GameStatus.IN_PROGRESS, 14, 10);
        when(games.findAllById(anyIterable())).thenReturn(List.of(live));
        when(cfbd.games(2026)).thenReturn(List.of(dto(101L, false, null, null)));

        service.ingestSchedule(2026);

        assertThat(live.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(live.getHomeScore()).isEqualTo(14);
    }

    /**
     * Not even when CFBD has a score to offer. Two sources writing the same
     * column is how they disagree; the poller is the one that runs every
     * minute, so it wins by default rather than by whoever ran last.
     */
    @Test
    void ignoresScoresCfbdDoesSupply() {
        Game settled = existingGame(102L, GameStatus.FINAL, 31, 17);
        when(games.findAllById(anyIterable())).thenReturn(List.of(settled));
        when(cfbd.games(2026)).thenReturn(List.of(dto(102L, true, 28, 21)));

        service.ingestSchedule(2026);

        assertThat(settled.getHomeScore())
                .as("the poller's score stands; ingestScores is the path that overwrites")
                .isEqualTo(31);
        assertThat(settled.getAwayScore()).isEqualTo(17);
    }

    /**
     * The reason the job is daily and season-wide: a kickoff moved for
     * television weeks out has to land, because pick lock windows are computed
     * from it.
     */
    @Test
    void stillUpdatesKickoffAndWeek() {
        Game existing = existingGame(103L, GameStatus.SCHEDULED, null, null);
        Instant moved = Instant.parse("2026-10-17T23:30:00Z");
        when(games.findAllById(anyIterable())).thenReturn(List.of(existing));
        when(cfbd.games(2026)).thenReturn(List.of(new CfbdDtos.GameDto(
                103L, 2026, 8, "regular", moved, false, false, false, true, "Jordan-Hare",
                1, "Auburn", "SEC", null, null,
                2, "Alabama", "SEC", null, null,
                null, null, null)));

        service.ingestSchedule(2026);

        assertThat(existing.getKickoff()).isEqualTo(moved);
        assertThat(existing.getWeek()).isEqualTo(8);
        assertThat(existing.getVenue()).isEqualTo("Jordan-Hare");
    }

    /** A game we have never seen is SCHEDULED - the entity's own default. */
    @Test
    void givesANewGameTheScheduledStatusAndNoScore() {
        when(games.findAllById(anyIterable())).thenReturn(List.of());
        when(cfbd.games(2026)).thenReturn(List.of(dto(104L, false, null, null)));

        List<Game> saved = captureSaved();

        assertThat(saved).singleElement().satisfies(game -> {
            assertThat(game.getStatus()).isEqualTo(GameStatus.SCHEDULED);
            assertThat(game.getHomeScore()).isNull();
            assertThat(game.getAwayScore()).isNull();
        });
    }

    @SuppressWarnings("unchecked")
    private List<Game> captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        service.ingestSchedule(2026);
        org.mockito.Mockito.verify(games).saveAll(captor.capture());
        List<Game> saved = new java.util.ArrayList<>();
        ((Iterable<Game>) captor.getValue()).forEach(saved::add);
        return saved;
    }

    private static Game existingGame(long id, GameStatus status, Integer home, Integer away) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(7);
        game.setSeasonType("regular");
        game.setKickoff(Instant.now().minus(2, ChronoUnit.DAYS));
        game.setStatus(status);
        game.setHomeScore(home);
        game.setAwayScore(away);
        return game;
    }

    private static CfbdDtos.GameDto dto(long id, boolean completed, Integer home, Integer away) {
        return new CfbdDtos.GameDto(
                id, 2026, 7, "regular", Instant.now().minus(2, ChronoUnit.DAYS), false,
                completed, false, true, "Jordan-Hare",
                1, "Auburn", "SEC", home, null,
                2, "Alabama", "SEC", away, null,
                null, null, null);
    }
}
