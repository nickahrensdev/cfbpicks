package com.nickspicks.api.team;

import com.nickspicks.api.athlete.Athlete;
import com.nickspicks.api.athlete.AthleteRepository;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.coach.CoachRepository;
import com.nickspicks.api.coach.CoachSeason;
import com.nickspicks.api.coach.CoachSeasonRepository;
import com.nickspicks.api.espn.EspnClient;
import com.nickspicks.api.game.GameService;
import com.nickspicks.api.ingest.ReferenceIngestService;
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

    private final TeamRepository teams;
    private final AthleteRepository athletes;
    private final CoachRepository coaches;
    private final CoachSeasonRepository coachSeasons;
    private final ReferenceIngestService referenceIngest;
    private final GameService gameService;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;
    private final DtoMapper mapper;
    private final RankingService rankings;
    private final EspnClient espn;

    public TeamController(TeamRepository teams, AthleteRepository athletes, CoachRepository coaches,
                          CoachSeasonRepository coachSeasons,
                          ReferenceIngestService referenceIngest, GameService gameService,
                          CurrentWeekResolver weeks, CurrentUserService currentUser,
                          DtoMapper mapper, RankingService rankings, EspnClient espn) {
        this.espn = espn;
        this.rankings = rankings;
        this.teams = teams;
        this.athletes = athletes;
        this.coaches = coaches;
        this.coachSeasons = coachSeasons;
        this.referenceIngest = referenceIngest;
        this.gameService = gameService;
        this.weeks = weeks;
        this.currentUser = currentUser;
        this.mapper = mapper;
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
    public ApiDtos.TeamDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        Team team = teams.findById(id)
                .orElseThrow(() -> new NotFoundException("Team %d not found".formatted(id)));

        int season = weeks.currentSeason();
        UUID userId = currentUser.resolveId(jwt);

        // The roster is fetched the first time someone opens this page and
        // never again - one API call per team for the life of the season.
        // Works for FCS as well as FBS.
        if (!athletes.existsByTeamIdAndSeason(id, season)) {
            try {
                referenceIngest.ensureRoster(team, season);
            } catch (CfbdUnavailableException ex) {
                // A missing roster should degrade the page, not break it.
                log.warn("Roster unavailable for {}: {}", team.getSchool(), ex.getMessage());
            }
        }

        ApiDtos.TeamSummary summary = mapper.teamSummary(team);

        List<ApiDtos.AthleteSummary> roster =
                athletes.findAllByTeamIdAndSeasonOrderByJerseyAsc(id, season).stream()
                        .sorted(Comparator.comparing(Athlete::getJersey,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(athlete -> mapper.athleteSummary(athlete, summary))
                        .toList();

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

        return new ApiDtos.TeamDetail(team.getId(), team.getSchool(), team.getMascot(),
                team.getAbbreviation(), team.getConference(), team.getDivision(), team.getColor(),
                team.getAlternateColor(), team.getLogoUrl(), team.getTwitter(), team.getVenueName(),
                team.getVenueCity(), team.getVenueState(), team.getVenueCapacity(),
                staff, roster, gameService.teamSchedule(season, id, userId),
                headlineRank, current, history,
                // Supplements what CFBD gives us with ESPN branding and venue
                // detail. Optional by design - null just means a plainer page.
                espn.team(id).orElse(null));
    }

    /** Orders polls the same way the rank beside a name is chosen. */
    private int pollPriority(String pollName) {
        Poll poll = Poll.fromCfbdName(pollName);
        return poll == null ? Integer.MAX_VALUE : Poll.priorityOrder().indexOf(poll);
    }
}
