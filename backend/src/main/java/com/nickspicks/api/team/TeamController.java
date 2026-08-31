package com.nickspicks.api.team;

import com.nickspicks.api.coach.CoachRepository;
import com.nickspicks.api.coach.CoachSeason;
import com.nickspicks.api.coach.CoachSeasonRepository;
import com.nickspicks.api.espn.EspnClient;
import com.nickspicks.api.espn.EspnRosterService;
import com.nickspicks.api.game.GameService;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupService;
import com.nickspicks.api.ranking.Poll;
import com.nickspicks.api.ranking.PollRanking;
import com.nickspicks.api.ranking.RankingService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);

    private final EspnRosterService rosters;
    private final TeamRepository teams;
    private final CoachRepository coaches;
    private final CoachSeasonRepository coachSeasons;
    private final GameService gameService;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;
    private final RankingService rankings;
    private final EspnClient espn;
    private final TeamRecordRepository teamRecords;
    private final TeamAtsService teamAtsService;
    private final TeamMatchupService matchupService;
    private final GroupService groups;

    public TeamController(TeamRepository teams, CoachRepository coaches,
                          CoachSeasonRepository coachSeasons,
                          EspnRosterService rosters, GameService gameService,
                          CurrentWeekResolver weeks, CurrentUserService currentUser,
                          DtoMapper mapper, RankingService rankings, EspnClient espn,
                          TeamRecordRepository teamRecords, TeamAtsService teamAtsService,
                          TeamMatchupService matchupService, GroupService groups) {
        this.groups = groups;
        this.espn = espn;
        this.rankings = rankings;
        this.rosters = rosters;
        this.teams = teams;
        this.coaches = coaches;
        this.coachSeasons = coachSeasons;
        this.gameService = gameService;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.teamRecords = teamRecords;
        this.teamAtsService = teamAtsService;
        this.matchupService = matchupService;
    }

    @GetMapping
    public List<ApiDtos.TeamSummary> list(@RequestParam(required = false) String conference,
                                          @RequestParam(required = false) String search) {
        List<Team> found;
        if (search != null && !search.isBlank()) {
            found = teams.findTop25BySchoolContainingIgnoreCaseOrderBySchoolAsc(search.trim());
        } else if (conference != null && !conference.isBlank()) {
            found = teams.findAllByConferenceIgnoreCaseOrderBySchoolAsc(conference.trim());
        } else {
            found = teams.findAllByOrderBySchoolAsc();
        }
        return found.stream().map(mapper::teamSummary).toList();
    }

    @GetMapping("/{id}")
    public ApiDtos.TeamDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                     @RequestParam(required = false) UUID groupId) {
        Team team = teams.findById(id)
                .orElseThrow(() -> new NotFoundException("Team %d not found".formatted(id)));

        int season = weeks.currentSeason();
        UUID userId = currentUser.resolveId(jwt);

        // Optional: without a group the schedule still renders, just without
        // the caller's picks marked on it.
        Group group = groupId == null ? null : groups.requirePlayable(groupId, userId);

        ApiDtos.TeamSummary summary = mapper.teamSummary(team);

        // Read live from ESPN and not stored anywhere. Unmetered, cached for
        // twelve hours in the client, and always current - the roster this
        // replaced was frozen at whenever a team was first opened, so a
        // transfer never showed. Empty rather than throwing if ESPN is down:
        // that costs the tab, not the page.
        List<ApiDtos.AthleteSummary> roster = rosters.roster(id, summary);

        List<ApiDtos.CoachSummary> staff = coachSeasons.findAllByTeamIdAndSeason(id, season).stream()
                .map(CoachSeason::getCoachId)
                .distinct()
                .map(coaches::findById)
                .flatMap(java.util.Optional::stream)
                .map(mapper::coachSummary)
                .sorted(Comparator.comparing(ApiDtos.CoachSummary::lastName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Rankings: the headline rank comes from the same priority poll used
        // everywhere else, while the history section shows all three so a
        // reader can see where the polls disagree.
        List<ApiDtos.PollPlacement> current = rankings.currentPollsForTeam(season, id).stream()
                .sorted(Comparator.comparingInt(row -> pollPriority(row.getPoll())))
                .map(row -> new ApiDtos.PollPlacement(row.getPoll(), row.getWeek(), row.getRank(),
                        row.getFirstPlaceVotes(), row.getPoints()))
                .toList();

        Integer headlineRank = rankings.rankLookup(season, null).get(id);

        List<ApiDtos.RankingHistoryWeek> history = rankings.teamHistory(season, id).stream()
                .collect(Collectors.groupingBy(PollRanking::getWeek, TreeMap::new, Collectors.toList()))
                .descendingMap()
                .entrySet().stream()
                .map(entry -> new ApiDtos.RankingHistoryWeek(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(row -> pollPriority(row.getPoll())))
                                .map(row -> new ApiDtos.PollPlacement(row.getPoll(), row.getWeek(),
                                        row.getRank(), row.getFirstPlaceVotes(), row.getPoints()))
                                .toList()))
                .toList();

        // One read covers both the season being viewed and the full history
        // below it, rather than querying the same table twice.
        List<TeamAts> atsHistory = teamAtsService.history(id);
        TeamAts ats = atsHistory.stream()
                .filter(row -> Integer.valueOf(season).equals(row.getSeason()))
                .findFirst()
                .orElse(null);

        return new ApiDtos.TeamDetail(team.getId(), team.getSchool(), team.getMascot(),
                team.getAbbreviation(), team.getConference(), team.getDivision(), team.getColor(),
                team.getAlternateColor(), team.getLogoUrl(), team.getTwitter(), team.getVenueName(),
                team.getVenueCity(), team.getVenueState(), team.getVenueCapacity(),
                staff, roster, gameService.teamSchedule(group, season, id, userId),
                headlineRank, current, history,
                // Supplements what CFBD gives us with ESPN branding and venue
                // detail. Optional by design - null just means a plainer page.
                espn.team(id).orElse(null),
                mapper.recordSummary(teamRecords.findByTeamIdAndSeason(id, season).orElse(null)),
                mapper.atsSummary(ats),
                mapper.atsHistory(atsHistory));
    }

    /**
     * All-time head-to-head history between two programs. Ordinary
     * authenticated content, not admin-gated - the on-demand caching in
     * {@link TeamMatchupService} is what bounds the cost, not a permission
     * check.
     */
    @GetMapping("/matchup")
    public ApiDtos.MatchupSummary matchup(@RequestParam int team1Id, @RequestParam int team2Id) {
        TeamMatchupService.Matchup matchup = matchupService.ensureFresh(team1Id, team2Id);
        if (matchup == null) {
            return new ApiDtos.MatchupSummary(team1Id, schoolName(team1Id), team2Id,
                    schoolName(team2Id), null, null, null, List.of());
        }

        // The service canonicalizes by (min id, max id); report wins back out
        // in the order the caller actually asked for the two teams.
        boolean requestedAFirst = team1Id <= team2Id;
        Integer team1Wins = requestedAFirst ? matchup.teamAWins() : matchup.teamBWins();
        Integer team2Wins = requestedAFirst ? matchup.teamBWins() : matchup.teamAWins();

        List<ApiDtos.MatchupGame> games = matchup.games().stream()
                .map(g -> new ApiDtos.MatchupGame(g.season(), g.week(), g.seasonType(), g.date(),
                        Boolean.TRUE.equals(g.neutralSite()), g.venue(), g.homeTeam(), g.homeScore(),
                        g.awayTeam(), g.awayScore(), g.winner()))
                .toList();

        return new ApiDtos.MatchupSummary(team1Id, schoolName(team1Id), team2Id, schoolName(team2Id),
                team1Wins, team2Wins, matchup.ties(), games);
    }

    private String schoolName(int teamId) {
        return teams.findById(teamId).map(Team::getSchool).orElse(null);
    }

    /** Orders polls the same way the rank beside a name is chosen. */
    private int pollPriority(String pollName) {
        Poll poll = Poll.fromCfbdName(pollName);
        return poll == null ? Integer.MAX_VALUE : Poll.priorityOrder().indexOf(poll);
    }
}
