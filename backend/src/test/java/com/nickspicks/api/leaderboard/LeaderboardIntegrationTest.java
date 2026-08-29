package com.nickspicks.api.leaderboard;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.ingest.GradingService;
import com.nickspicks.api.pick.Pick;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickResult;
import com.nickspicks.api.pick.PickService;
import com.nickspicks.api.pick.Selection;
import com.nickspicks.api.pick.WeeklyEntryRepository;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The full chain: pick, game goes final, grade, standings, ranking. */
class LeaderboardIntegrationTest extends IntegrationTest {

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

    @Autowired
    private GradingService grading;

    @Autowired
    private StandingsRepository standings;

    @Override
    protected void cleanUp() {
        pickRepository.deleteAll();
        entries.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    @Test
    void gradesPicksAndRanksMembersByWinsThenFewestLosses() {
        UUID winner = member("winner");
        UUID middle = member("middle");
        UUID loser = member("loser");

        // Home favored by 7.5 in all three.
        Game one = game(1L);
        Game two = game(2L);
        Game three = game(3L);

        // Both games are home -7.5:
        //   game one finishes 31-20, so home covers  -> HOME wins, AWAY loses
        //   game two finishes 24-20, home wins but does not cover the 7.5
        //                            -> HOME loses,  AWAY wins
        //
        // winner 2-0, middle 1-1, loser 0-2.
        picks.create(winner, one.getId(), Selection.HOME);
        picks.create(winner, two.getId(), Selection.AWAY);
        picks.create(middle, one.getId(), Selection.HOME);
        picks.create(middle, two.getId(), Selection.HOME);
        picks.create(loser, one.getId(), Selection.AWAY);
        picks.create(loser, two.getId(), Selection.HOME);

        // A push for everyone on game three - must not count either way.
        picks.create(winner, three.getId(), Selection.HOME);
        picks.create(middle, three.getId(), Selection.AWAY);

        finish(one, 31, 20);    // home covers -7.5
        finish(two, 24, 20);    // home wins but fails to cover
        finishExact(three, 27, 20, new BigDecimal("-7.0")); // exactly 7 - push

        assertThat(grading.gradeGame(games.findById(1L).orElseThrow())).isEqualTo(3);
        assertThat(grading.gradeGame(games.findById(2L).orElseThrow())).isEqualTo(3);
        assertThat(grading.gradeGame(games.findById(3L).orElseThrow())).isEqualTo(2);

        List<StandingsView> table = standings.leaderboard(2026);
        assertThat(table).extracting(StandingsView::getDisplayName)
                .containsExactly("winner", "middle", "loser");

        StandingsView top = table.get(0);
        assertThat(top.getWins()).isEqualTo(2);
        assertThat(top.getLosses()).isZero();
        assertThat(top.getPushes()).isEqualTo(1);
        // Pushes are counted but do not affect the ranking.
        assertThat(top.getGamesGraded()).isEqualTo(3);

        assertThat(table.get(2).getWins()).isZero();
        assertThat(table.get(2).getLosses()).isEqualTo(2);
    }

    @Test
    void voidsPicksOnCanceledGamesInsteadOfCountingThemAsLosses() {
        UUID user = member("unlucky");
        Game canceled = game(10L);
        picks.create(user, canceled.getId(), Selection.HOME);

        canceled.setStatus(GameStatus.CANCELED);
        games.save(canceled);
        grading.gradeGame(canceled);

        assertThat(pickRepository.findAllByGameId(10L))
                .extracting(Pick::getResult)
                .containsExactly(PickResult.VOID);

        // A voided pick keeps the member off the standings entirely rather
        // than showing an 0-0 row.
        assertThat(standings.leaderboard(2026)).isEmpty();
    }

    // ------------------------------------------------------------- fixtures

    private UUID member(String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name));
        return id;
    }

    private Game game(long id) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(1);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(Instant.now().plus(2, ChronoUnit.DAYS));
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }

    private void finish(Game game, int homeScore, int awayScore) {
        game.setHomeScore(homeScore);
        game.setAwayScore(awayScore);
        game.setStatus(GameStatus.FINAL);
        games.save(game);
    }

    private void finishExact(Game game, int homeScore, int awayScore, BigDecimal spread) {
        // The picks already locked -7.5; move them to the whole number so the
        // push path is exercised.
        pickRepository.findAllByGameId(game.getId()).forEach(pick -> {
            pick.setLockedLine(spread);
            pickRepository.save(pick);
        });
        finish(game, homeScore, awayScore);
    }
}
