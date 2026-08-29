package com.nickspicks.api.game;

import com.nickspicks.api.espn.EspnGameService;
import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.pick.Pick;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.pick.PickWindow;
import com.nickspicks.api.ranking.RankingService;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRepository;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GameService {

    private final GameRepository games;
    private final PickRepository picks;
    private final TeamRepository teams;
    private final AppUserRepository users;
    private final PickWindow window;
    private final DtoMapper mapper;
    private final RankingService rankings;
    private final LiveScoreService liveScores;
    private final EspnGameService espnGames;

    public GameService(GameRepository games, PickRepository picks, TeamRepository teams,
                       AppUserRepository users, PickWindow window, DtoMapper mapper,
                       RankingService rankings, LiveScoreService liveScores,
                       EspnGameService espnGames) {
        this.liveScores = liveScores;
        this.espnGames = espnGames;
        this.games = games;
        this.picks = picks;
        this.teams = teams;
        this.users = users;
        this.window = window;
        this.mapper = mapper;
        this.rankings = rankings;
    }

    /**
     * Filters for the games page. Any null field is ignored, so the same
     * method serves the unfiltered listing.
     */
    public record GameFilter(String conference, Integer teamId,
                             Double minSpread, Double maxSpread) {

        static final GameFilter NONE = new GameFilter(null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.GameSummary> listWeek(int season, int week, UUID userId) {
        return listWeek(season, week, userId, GameFilter.NONE);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.GameSummary> listWeek(int season, int week, UUID userId,
                                              GameFilter filter) {
        List<Game> weekGames = games.findAllBySeasonAndWeekOrderByKickoffAsc(season, week);

        // One team lookup for the whole page rather than two per game.
        Map<Integer, Team> teamCache = teamCache();
        // Ranks as of the week being viewed, not today's - looking back at
        // week 3 should show who was ranked going into week 3.
        Map<Integer, Integer> ranks = rankings.rankLookup(season, week);
        // Grouped, not keyed: a member can hold both a spread and a total on
        // the same game.
        Map<Long, List<Pick>> myPicks = picks.findForUserWeek(userId, season, week).stream()
                .collect(Collectors.groupingBy(Pick::getGameId));

        Map<Long, LiveScoreService.LiveGame> live = liveScoresFor(weekGames);

        return weekGames.stream()
                .filter(game -> matches(game, filter))
                .map(game -> mapper.gameSummary(game, myPicks.get(game.getId()), teamCache, ranks,
                        live))
                .toList();
    }

    /**
     * ESPN's live board, but only when one of these games could plausibly be
     * on. A Tuesday afternoon should not call out to a third party to be told
     * that nothing is happening.
     */
    private Map<Long, LiveScoreService.LiveGame> liveScoresFor(List<Game> candidates) {
        Instant now = Instant.now();
        boolean anyLive = candidates.stream().anyMatch(game ->
                game.getStatus() != GameStatus.FINAL
                        && game.getKickoff() != null
                        && game.getKickoff().isBefore(now)
                        // No game runs longer than six hours; past that, a row
                        // still marked unfinished is stale data, not a game.
                        && game.getKickoff().isAfter(now.minus(6, ChronoUnit.HOURS)));

        return anyLive ? liveScores.current() : Map.of();
    }

    /** Conferences and teams playing in a given week, for the filter controls. */
    @Transactional(readOnly = true)
    public ApiDtos.GameFilters filterOptions(int season, int week) {
        List<Game> weekGames = games.findAllBySeasonAndWeekOrderByKickoffAsc(season, week);
        Map<Integer, Team> teamCache = teamCache();

        List<String> conferences = weekGames.stream()
                .flatMap(game -> Stream.of(game.getHomeConference(), game.getAwayConference()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<ApiDtos.TeamSummary> teams = weekGames.stream()
                .flatMap(game -> Stream.of(game.getHomeTeamId(), game.getAwayTeamId()))
                .filter(Objects::nonNull)
                .distinct()
                .map(teamCache::get)
                .filter(Objects::nonNull)
                .map(mapper::teamSummary)
                .sorted(Comparator.comparing(ApiDtos.TeamSummary::school,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        // The widest line on the board, so the slider's ceiling matches
        // reality instead of an arbitrary constant.
        double maxSpread = weekGames.stream()
                .map(Game::getHomeSpread)
                .filter(Objects::nonNull)
                .mapToDouble(spread -> Math.abs(spread.doubleValue()))
                .max()
                .orElse(0);

        return new ApiDtos.GameFilters(conferences, teams, Math.ceil(maxSpread));
    }

    private boolean matches(Game game, GameFilter filter) {
        if (filter.conference() != null
                && !filter.conference().equalsIgnoreCase(game.getHomeConference())
                && !filter.conference().equalsIgnoreCase(game.getAwayConference())) {
            return false;
        }
        if (filter.teamId() != null
                && !filter.teamId().equals(game.getHomeTeamId())
                && !filter.teamId().equals(game.getAwayTeamId())) {
            return false;
        }
        if (filter.minSpread() != null || filter.maxSpread() != null) {
            // A game with no line has no spread size, so it cannot satisfy a
            // spread filter - dropping it is less confusing than showing it.
            if (game.getHomeSpread() == null) {
                return false;
            }
            double size = Math.abs(game.getHomeSpread().doubleValue());
            if (filter.minSpread() != null && size < filter.minSpread()) {
                return false;
            }
            if (filter.maxSpread() != null && size > filter.maxSpread()) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public ApiDtos.GameDetail detail(long gameId, UUID userId) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));

        Map<Integer, Team> teamCache = teamCache();
        Map<Integer, Integer> ranks = rankings.rankLookup(game.getSeason(), game.getWeek());
        List<Pick> myPicks = picks.findAllByUserIdAndGameId(userId, gameId);

        // Everyone's picks are visible only once the game has started, so the
        // detail page cannot be used to scout the field beforehand.
        boolean revealed = window.isRevealed(game, Instant.now());
        List<ApiDtos.MemberPick> memberPicks = revealed ? memberPicks(gameId) : List.of();

        return new ApiDtos.GameDetail(
                mapper.gameSummary(game, myPicks, teamCache, ranks, liveScoresFor(List.of(game))),
                game.getSpreadOpen(),
                game.getOverUnder(),
                game.getOverUnderOpen(),
                game.getHomeMoneyline(),
                game.getAwayMoneyline(),
                game.getSpreadProvider(),
                game.getSpreadUpdatedAt(),
                game.getHomePregameElo(),
                game.getAwayPregameElo(),
                game.getHomePostgameWinProbability(),
                game.getAwayPostgameWinProbability(),
                game.getExcitementIndex(),
                game.getHomeConference(),
                game.getAwayConference(),
                game.isConferenceGame(),
                memberPicks,
                revealed,
                // Box score, leaders and venue detail. A page that renders
                // without it is the normal case before kickoff.
                espnGames.summary(gameId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.GameSummary> teamSchedule(int season, int teamId, UUID userId) {
        Map<Integer, Team> teamCache = teamCache();
        Map<Long, List<Pick>> myPicks = picks.findAllByUserId(userId).stream()
                .collect(Collectors.groupingBy(Pick::getGameId));

        // Each row shows the ranks that applied in that game's own week, so a
        // schedule reads the way a record book does.
        Map<Integer, Map<Integer, Integer>> ranksByWeek = new HashMap<>();

        List<Game> schedule = games.findSeasonScheduleForTeam(season, teamId);
        Map<Long, LiveScoreService.LiveGame> live = liveScoresFor(schedule);

        return schedule.stream()
                .map(game -> mapper.gameSummary(game, myPicks.get(game.getId()), teamCache,
                        ranksByWeek.computeIfAbsent(game.getWeek(),
                                week -> rankings.rankLookup(season, week)),
                        live))
                .toList();
    }

    private List<ApiDtos.MemberPick> memberPicks(long gameId) {
        Map<UUID, AppUser> members = users.findAll().stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return picks.findAllByGameId(gameId).stream()
                .map(pick -> new ApiDtos.MemberPick(
                        pick.getUserId(),
                        members.containsKey(pick.getUserId())
                                ? members.get(pick.getUserId()).getDisplayName()
                                : "Unknown",
                        pick.getSelection(),
                        pick.getMarket(),
                        pick.getLockedLine(),
                        pick.getResult()))
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()))
                .toList();
    }

    private Map<Integer, Team> teamCache() {
        return teams.findAll().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity(), (a, b) -> a));
    }
}
