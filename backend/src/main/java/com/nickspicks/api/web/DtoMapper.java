package com.nickspicks.api.web;

import com.nickspicks.api.athlete.Athlete;
import com.nickspicks.api.coach.Coach;
import com.nickspicks.api.coach.CoachSeason;
import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.pick.Market;
import com.nickspicks.api.pick.Pick;
import com.nickspicks.api.pick.PickWindow;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamAts;
import com.nickspicks.api.team.TeamRecord;
import com.nickspicks.api.team.TeamRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Builds API responses from entities.
 *
 * <p>Team summaries are embedded in games, rosters and schedules so that every
 * team name the UI renders arrives with the id, logo and colour it needs to be
 * a link - the front end never has to guess or look one up.
 */
@Component
public class DtoMapper {

    private final TeamRepository teams;
    private final PickWindow window;

    public DtoMapper(TeamRepository teams, PickWindow window) {
        this.teams = teams;
        this.window = window;
    }

    public ApiDtos.TeamSummary teamSummary(Team team) {
        return teamSummary(team, null);
    }

    public ApiDtos.AtsSummary atsSummary(TeamAts ats) {
        if (ats == null) {
            return null;
        }
        return new ApiDtos.AtsSummary(ats.getGames(), ats.getAtsWins(), ats.getAtsLosses(),
                ats.getAtsPushes(), ats.getAvgCoverMargin());
    }

    public ApiDtos.RecordSummary recordSummary(TeamRecord row) {
        if (row == null) {
            return null;
        }
        return new ApiDtos.RecordSummary(
                row.getExpectedWins(),
                new ApiDtos.WinLossSplit(row.getTotalGames(), row.getTotalWins(),
                        row.getTotalLosses(), row.getTotalTies()),
                new ApiDtos.WinLossSplit(row.getConferenceGames(), row.getConferenceWins(),
                        row.getConferenceLosses(), row.getConferenceTies()),
                new ApiDtos.WinLossSplit(row.getHomeGames(), row.getHomeWins(),
                        row.getHomeLosses(), row.getHomeTies()),
                new ApiDtos.WinLossSplit(row.getAwayGames(), row.getAwayWins(),
                        row.getAwayLosses(), row.getAwayTies()),
                new ApiDtos.WinLossSplit(row.getNeutralGames(), row.getNeutralWins(),
                        row.getNeutralLosses(), row.getNeutralTies()),
                new ApiDtos.WinLossSplit(row.getRegularGames(), row.getRegularWins(),
                        row.getRegularLosses(), row.getRegularTies()),
                new ApiDtos.WinLossSplit(row.getPostseasonGames(), row.getPostseasonWins(),
                        row.getPostseasonLosses(), row.getPostseasonTies()));
    }

    /**
     * @param ranks team id → poll rank for the week in view, or null when the
     *              caller has no week context (the summary then carries no
     *              rank rather than a possibly-wrong one).
     */
    public ApiDtos.TeamSummary teamSummary(Team team, Map<Integer, Integer> ranks) {
        if (team == null) {
            return null;
        }
        Integer rank = ranks == null ? null : ranks.get(team.getId());
        return new ApiDtos.TeamSummary(team.getId(), team.getSchool(), team.getMascot(),
                team.getAbbreviation(), team.getConference(), team.getLogoUrl(), team.getColor(),
                rank);
    }

    public ApiDtos.TeamSummary teamSummary(Integer teamId) {
        return teamSummary(teamId, null);
    }

    public ApiDtos.TeamSummary teamSummary(Integer teamId, Map<Integer, Integer> ranks) {
        return teamId == null
                ? null
                : teams.findById(teamId).map(team -> teamSummary(team, ranks)).orElse(null);
    }

    public ApiDtos.AthleteSummary athleteSummary(Athlete athlete, ApiDtos.TeamSummary team) {
        return new ApiDtos.AthleteSummary(athlete.getId(), athlete.getFirstName(),
                athlete.getLastName(), athlete.getPosition(), athlete.getJersey(),
                athlete.getYear(), team);
    }

    public ApiDtos.CoachSummary coachSummary(Coach coach) {
        return new ApiDtos.CoachSummary(coach.getId(), coach.getFirstName(), coach.getLastName(),
                coach.getHireDate());
    }

    public ApiDtos.CoachSeasonRow coachSeasonRow(CoachSeason season) {
        return new ApiDtos.CoachSeasonRow(season.getSeason(), season.getSchool(),
                season.getTeamId(), season.getConference(), season.getGames(), season.getWins(),
                season.getLosses(), season.getTies(), season.getSpOverall());
    }

    public ApiDtos.PickSummary pickSummary(Pick pick) {
        if (pick == null) {
            return null;
        }
        return new ApiDtos.PickSummary(pick.getId(), pick.getGameId(), pick.getSelection(),
                pick.getMarket(), pick.getLockedLine(), pick.getResult(), pick.getCreatedAt(),
                pick.getUpdatedAt());
    }

    public ApiDtos.GameSummary gameSummary(Game game, Collection<Pick> myPicks,
                                           Map<Integer, Team> teamCache) {
        return gameSummary(game, myPicks, teamCache, null, null);
    }

    public ApiDtos.GameSummary gameSummary(Game game, Collection<Pick> myPicks,
                                           Map<Integer, Team> teamCache,
                                           Map<Integer, Integer> ranks) {
        return gameSummary(game, myPicks, teamCache, ranks, null);
    }

    /**
     * @param myPicks the caller's picks on this game - at most one per market,
     *                and often none
     * @param live    ESPN's view of games currently being played, keyed by game
     *                id. Null or absent means nothing live to show.
     */
    public ApiDtos.GameSummary gameSummary(Game game, Collection<Pick> myPicks,
                                           Map<Integer, Team> teamCache,
                                           Map<Integer, Integer> ranks,
                                           Map<Long, LiveScoreService.LiveGame> live) {
        Instant now = Instant.now();

        Pick spreadPick = pickFor(myPicks, Market.SPREAD);
        Pick totalPick = pickFor(myPicks, Market.TOTAL);
        boolean open = window.isOpen(game, now);

        return new ApiDtos.GameSummary(
                game.getId(),
                game.getSeason(),
                game.getWeek(),
                teamSummary(lookup(teamCache, game.getHomeTeamId()), ranks),
                teamSummary(lookup(teamCache, game.getAwayTeamId()), ranks),
                game.getHomeTeam(),
                game.getAwayTeam(),
                game.getHomeConference(),
                game.getAwayConference(),
                game.getKickoff(),
                game.isStartTimeTbd(),
                game.isNeutralSite(),
                game.getVenue(),
                game.getHomeSpread(),
                game.getOverUnder(),
                game.getStatus().name(),
                game.getHomeScore(),
                game.getAwayScore(),
                !open,
                window.locksAt(game),
                pickSummary(spreadPick),
                pickSummary(totalPick),
                spreadPick != null && open && window.isLineImproved(spreadPick, game),
                totalPick != null && open && window.isLineImproved(totalPick, game),
                liveFor(live, game));
    }

    /**
     * ESPN's live state for this game, but only while it is actually being
     * played. A finished game already has its stored score, and letting a
     * "post" row through would put a second, unsettled score beside it.
     */
    private LiveScoreService.LiveGame liveFor(Map<Long, LiveScoreService.LiveGame> live,
                                              Game game) {
        if (live == null) {
            return null;
        }
        LiveScoreService.LiveGame found = live.get(game.getId());
        return found != null && found.inProgress() ? found : null;
    }

    private Pick pickFor(Collection<Pick> picks, Market market) {
        if (picks == null) {
            return null;
        }
        return picks.stream()
                .filter(pick -> pick.getMarket() == market)
                .findFirst()
                .orElse(null);
    }

    private Team lookup(Map<Integer, Team> cache, Integer id) {
        if (id == null) {
            return null;
        }
        if (cache != null) {
            return cache.get(id);
        }
        return teams.findById(id).orElse(null);
    }
}
