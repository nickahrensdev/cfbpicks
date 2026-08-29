package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Schedules, lines and scores. Upserts by CFBD game id, so re-running any of
 * these is safe.
 */
@Service
public class GameIngestService {

    private static final Logger log = LoggerFactory.getLogger(GameIngestService.class);

    /**
     * Preference order when several books post a line. Consistency matters
     * more than which book - members should see one number, not a different
     * one on every refresh.
     */
    private static final List<String> PROVIDER_PREFERENCE =
            List.of("DraftKings", "Bovada", "ESPN Bet", "consensus");

    private final CfbdClient cfbd;
    private final GameRepository games;
    private final AppProperties properties;
    private final GradingService grading;

    public GameIngestService(CfbdClient cfbd, GameRepository games, AppProperties properties,
                             GradingService grading) {
        this.cfbd = cfbd;
        this.games = games;
        this.properties = properties;
        this.grading = grading;
    }

    /**
     * The whole season's schedule. One API call for all ~888 games.
     *
     * <p>Asking week by week costs one call each for exactly the same rows, so
     * this is both cheaper and gives members every future week to look at
     * rather than only the ones somebody remembered to load.
     */
    @Transactional
    public int ingestSchedule(int season) {
        List<CfbdDtos.GameDto> dtos = cfbd.games(season);

        for (CfbdDtos.GameDto dto : dtos) {
            if (dto.startDate() == null) {
                continue;
            }
            Game game = games.findById(dto.id()).orElseGet(Game::new);
            game.setId(dto.id());
            game.setSeason(dto.season());
            game.setWeek(dto.week());
            game.setSeasonType(dto.seasonType() == null ? "regular" : dto.seasonType());
            game.setHomeTeamId(dto.homeId());
            game.setHomeTeam(dto.homeTeam());
            game.setHomeConference(dto.homeConference());
            game.setAwayTeamId(dto.awayId());
            game.setAwayTeam(dto.awayTeam());
            game.setAwayConference(dto.awayConference());
            game.setNeutralSite(Boolean.TRUE.equals(dto.neutralSite()));
            game.setConferenceGame(Boolean.TRUE.equals(dto.conferenceGame()));
            game.setVenue(dto.venue());
            game.setKickoff(dto.startDate());
            game.setStartTimeTbd(Boolean.TRUE.equals(dto.startTimeTBD()));
            game.setHomePregameElo(dto.homePregameElo());
            game.setAwayPregameElo(dto.awayPregameElo());
            applyScore(game, dto);
            game.setUpdatedAt(Instant.now());
            games.save(game);
        }

        log.info("Ingested {} games for {}", dtos.size(), season);
        return dtos.size();
    }

    /**
     * Betting lines for the whole season. One API call.
     *
     * <p>Season-wide for the same reason the schedule is: /lines charges one
     * call whether you ask for one week or all of them, so narrowing by week
     * only means paying again for the next week's numbers.
     */
    @Transactional
    public int ingestLines(int season) {
        List<CfbdDtos.LineDto> dtos = cfbd.lines(season);
        int updated = 0;

        for (CfbdDtos.LineDto dto : dtos) {
            Game game = games.findById(dto.id()).orElse(null);
            if (game == null || dto.lines() == null || dto.lines().isEmpty()) {
                continue;
            }

            CfbdDtos.LineDto.LineEntry best = dto.lines().stream()
                    .filter(entry -> entry.spread() != null)
                    .min(Comparator.comparingInt(entry -> {
                        int index = PROVIDER_PREFERENCE.indexOf(entry.provider());
                        return index < 0 ? Integer.MAX_VALUE : index;
                    }))
                    .orElse(null);

            if (best == null) {
                continue;
            }

            // Line movement updates the game, never an existing pick's
            // locked_spread - a member is graded on the number they took.
            game.setHomeSpread(best.spread());
            game.setSpreadOpen(best.spreadOpen());
            game.setOverUnder(best.overUnder());
            game.setOverUnderOpen(best.overUnderOpen());
            game.setHomeMoneyline(best.homeMoneyline());
            game.setAwayMoneyline(best.awayMoneyline());
            game.setSpreadProvider(best.provider());
            game.setSpreadUpdatedAt(Instant.now());
            game.setUpdatedAt(Instant.now());
            games.save(game);
            updated++;
        }

        log.info("Updated lines on {} games for {}", updated, season);
        return updated;
    }

    /**
     * Scores for the season, then grades anything that just went final. One
     * API call. Callers are expected to check {@link #hasLiveGames()} first.
     */
    @Transactional
    public int ingestScores(int season) {
        List<CfbdDtos.GameDto> dtos = cfbd.games(season);
        int finalised = 0;

        for (CfbdDtos.GameDto dto : dtos) {
            Game game = games.findById(dto.id()).orElse(null);
            if (game == null) {
                continue;
            }
            GameStatus before = game.getStatus();
            applyScore(game, dto);
            game.setUpdatedAt(Instant.now());
            games.save(game);

            if (game.getStatus() == GameStatus.FINAL && before != GameStatus.FINAL) {
                finalised += grading.gradeGame(game);
            }
        }

        log.info("Scored {} games, graded picks on {} newly-final games", dtos.size(), finalised);
        return finalised;
    }

    /**
     * True when any game could plausibly be underway. The score poller calls
     * this first so a quiet Tuesday costs zero API calls.
     */
    public boolean hasLiveGames() {
        Instant now = Instant.now();
        return !games.findPotentiallyLive(now, now.minusSeconds(6 * 3600)).isEmpty();
    }

    public int currentSeason() {
        return properties.getPickem().getSeason();
    }

    private void applyScore(Game game, CfbdDtos.GameDto dto) {
        game.setHomeScore(dto.homePoints());
        game.setAwayScore(dto.awayPoints());
        // Postgame figures - null until the game finishes.
        game.setHomePostgameWinProbability(dto.homePostgameWinProbability());
        game.setAwayPostgameWinProbability(dto.awayPostgameWinProbability());
        game.setExcitementIndex(dto.excitementIndex());

        if (Boolean.TRUE.equals(dto.completed())) {
            game.setStatus(GameStatus.FINAL);
        } else if (game.getKickoff() != null && game.getKickoff().isBefore(Instant.now())) {
            game.setStatus(GameStatus.IN_PROGRESS);
        } else {
            game.setStatus(GameStatus.SCHEDULED);
        }
    }

    /** Only used by tests and the admin endpoint. */
    public BigDecimal spreadOf(Game game) {
        return game.getHomeSpread();
    }
}
