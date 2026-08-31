package com.nickspicks.api.ingest;

import com.nickspicks.api.athlete.Athlete;
import com.nickspicks.api.athlete.AthleteRepository;
import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.cfbd.CfbdSync;
import com.nickspicks.api.cfbd.CfbdSyncRepository;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.coach.Coach;
import com.nickspicks.api.coach.CoachRepository;
import com.nickspicks.api.coach.CoachSeason;
import com.nickspicks.api.coach.CoachSeasonRepository;
import com.nickspicks.api.ranking.Poll;
import com.nickspicks.api.ranking.PollRanking;
import com.nickspicks.api.ranking.PollRankingRepository;
import com.nickspicks.api.season.SeasonWeek;
import com.nickspicks.api.season.SeasonWeekRepository;
import com.nickspicks.api.team.Team;
import com.nickspicks.api.team.TeamRecord;
import com.nickspicks.api.team.TeamRecordRepository;
import com.nickspicks.api.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ingests the reference data behind the clickable team / athlete / coach
 * pages.
 *
 * <p>These are read from our own tables rather than proxied per page view.
 * Detail pages are user-triggered, and the CFBD free tier is 1,000 calls a
 * month - a handful of members browsing rosters would exhaust it in an
 * afternoon. Teams and coaches cost one call each per season; a roster costs
 * one call per team, once, the first time anyone opens that team's page.
 */
@Service
public class ReferenceIngestService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceIngestService.class);

    private static final String RESOURCE_CALENDAR = "calendar";
    private static final String RESOURCE_TEAMS = "teams";
    private static final String RESOURCE_COACHES = "coaches";

    private final CfbdClient cfbd;
    private final CfbdSyncRepository syncs;
    private final TeamRepository teams;
    private final AthleteRepository athletes;
    private final CoachRepository coaches;
    private final CoachSeasonRepository coachSeasons;
    private final SeasonWeekRepository seasonWeeks;
    private final PollRankingRepository rankings;
    private final TeamRecordRepository teamRecords;
    private final AppProperties properties;

    public ReferenceIngestService(CfbdClient cfbd, CfbdSyncRepository syncs, TeamRepository teams,
                                  AthleteRepository athletes, CoachRepository coaches,
                                  CoachSeasonRepository coachSeasons,
                                  SeasonWeekRepository seasonWeeks,
                                  PollRankingRepository rankings, TeamRecordRepository teamRecords,
                                  AppProperties properties) {
        this.rankings = rankings;
        this.teamRecords = teamRecords;
        this.cfbd = cfbd;
        this.syncs = syncs;
        this.teams = teams;
        this.athletes = athletes;
        this.coaches = coaches;
        this.coachSeasons = coachSeasons;
        this.seasonWeeks = seasonWeeks;
        this.properties = properties;
    }

    /**
     * Every poll for every week of the season, in one API call.
     *
     * <p>Only the three polls the site uses are kept - the feed also carries
     * FCS, D2 and D3 coaches polls. Re-running updates existing rows, so this
     * is the button to press each week as new polls publish.
     */
    @Transactional
    public int ingestRankings(int season) {
        List<CfbdDtos.RankingWeekDto> weeks = cfbd.rankings(season);
        int stored = 0;

        for (CfbdDtos.RankingWeekDto weekDto : weeks) {
            if (weekDto.week() == null || weekDto.polls() == null) {
                continue;
            }
            String seasonType = weekDto.seasonType() == null ? "regular" : weekDto.seasonType();

            for (CfbdDtos.RankingWeekDto.PollDto pollDto : weekDto.polls()) {
                Poll poll = Poll.fromCfbdName(pollDto.poll());
                if (poll == null || pollDto.ranks() == null) {
                    continue;
                }

                for (CfbdDtos.RankingWeekDto.RankDto rankDto : pollDto.ranks()) {
                    if (rankDto.rank() == null || rankDto.school() == null) {
                        continue;
                    }
                    PollRanking row = rankings
                            .findBySeasonAndWeekAndSeasonTypeAndPollAndSchool(
                                    season, weekDto.week(), seasonType, poll.cfbdName(),
                                    rankDto.school())
                            .orElseGet(PollRanking::new);

                    row.setSeason(season);
                    row.setWeek(weekDto.week());
                    row.setSeasonType(seasonType);
                    row.setPoll(poll.cfbdName());
                    row.setRank(rankDto.rank());
                    row.setTeamId(rankDto.teamId());
                    row.setSchool(rankDto.school());
                    row.setConference(rankDto.conference());
                    row.setFirstPlaceVotes(rankDto.firstPlaceVotes());
                    row.setPoints(rankDto.points());
                    row.setUpdatedAt(Instant.now());
                    rankings.save(row);
                    stored++;
                }
            }
        }

        log.info("Ingested {} ranking rows across {} weeks for {}", stored, weeks.size(), season);
        return stored;
    }

    /**
     * The season calendar. Lets the week selector offer weeks that have not
     * been ingested yet, so members can look ahead instead of only seeing
     * weeks somebody already loaded. One API call per season.
     */
    @Transactional
    public int ingestCalendar(int season) {
        List<CfbdDtos.CalendarWeek> dtos = cfbd.calendar(season);

        for (CfbdDtos.CalendarWeek dto : dtos) {
            if (dto.week() == null) {
                continue;
            }
            String type = dto.seasonType() == null ? "regular" : dto.seasonType();
            SeasonWeek week = seasonWeeks
                    .findById(new SeasonWeek.Key(season, dto.week(), type))
                    .orElseGet(SeasonWeek::new);
            week.setSeason(season);
            week.setWeek(dto.week());
            week.setSeasonType(type);
            week.setStartDate(dto.startDate());
            week.setEndDate(dto.endDate());
            week.setFirstGameStart(dto.firstGameStart());
            week.setLastGameStart(dto.lastGameStart());
            week.setUpdatedAt(Instant.now());
            seasonWeeks.save(week);
        }

        syncs.markSynced(RESOURCE_CALENDAR, String.valueOf(season));
        log.info("Ingested {} calendar weeks for {}", dtos.size(), season);
        return dtos.size();
    }

    /**
     * Every program in the configured classifications - FBS and FCS by
     * default. One API call regardless of how many divisions are kept, so
     * including FCS is free and turns non-FBS opponents from plain text into
     * real links.
     */
    @Transactional
    public int ingestTeams(int season) {
        List<String> wanted = properties.getCfbd().getTeamClassifications().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();

        List<CfbdDtos.TeamDto> dtos = cfbd.allTeams(season).stream()
                .filter(dto -> dto.id() != null && dto.school() != null)
                .filter(dto -> dto.classification() != null
                        && wanted.contains(dto.classification().toLowerCase(Locale.ROOT)))
                .toList();

        for (CfbdDtos.TeamDto dto : dtos) {
            Team team = teams.findById(dto.id()).orElseGet(Team::new);
            team.setId(dto.id());
            team.setSchool(dto.school());
            team.setMascot(dto.mascot());
            team.setAbbreviation(dto.abbreviation());
            team.setConference(dto.conference());
            team.setDivision(dto.division());
            team.setClassification(dto.classification());
            team.setColor(dto.color());
            team.setAlternateColor(dto.alternateColor());
            team.setTwitter(dto.twitter());
            team.setLogoUrl(pickLogo(dto.logos(), false));
            team.setLogoDarkUrl(pickLogo(dto.logos(), true));

            if (dto.location() != null) {
                team.setVenueName(dto.location().name());
                team.setVenueCity(dto.location().city());
                team.setVenueState(dto.location().state());
                team.setVenueCapacity(dto.location().capacity());
            }
            team.setUpdatedAt(Instant.now());
            teams.save(team);
        }

        syncs.markSynced(RESOURCE_TEAMS, String.valueOf(season));
        log.info("Ingested {} teams for {}", dtos.size(), season);
        return dtos.size();
    }

    /**
     * Coaches for the season.
     *
     * <p>FBS only, and that is an upstream limit rather than a choice: the
     * provider has no coach records for FCS programs. Asking for one by name
     * ({@code /coaches?team=Montana}) returns an empty array, so there is
     * nothing to fetch lazily and no per-team call worth spending quota on.
     */
    @Transactional
    public int ingestCoaches(int season) {
        List<CfbdDtos.CoachDto> dtos = cfbd.coaches(season);
        dtos.forEach(this::saveCoach);

        syncs.markSynced(RESOURCE_COACHES, String.valueOf(season));
        log.info("Ingested {} coaches for {}", dtos.size(), season);
        return dtos.size();
    }

    private void saveCoach(CfbdDtos.CoachDto dto) {
        if (dto.id() == null) {
            return;
        }
        Coach coach = coaches.findById(dto.id()).orElseGet(Coach::new);
        coach.setId(dto.id());
        coach.setFirstName(dto.firstName());
        coach.setLastName(dto.lastName());
        coach.setHireDate(dto.hireDate());
        coach.setUpdatedAt(Instant.now());
        coaches.save(coach);

        if (dto.seasons() == null) {
            return;
        }
        // Same unflushed-insert hazard as rosters: a repeated (year, school)
        // in one coach's history would collide on the primary key.
        Set<String> seenSeasons = new HashSet<>();

        for (CfbdDtos.CoachDto.CoachSeasonDto s : dto.seasons()) {
            if (s.year() == null || s.school() == null
                    || !seenSeasons.add(s.year() + "|" + s.school())) {
                continue;
            }
            CoachSeason cs = coachSeasons
                    .findById(new CoachSeason.Key(dto.id(), s.year(), s.school()))
                    .orElseGet(CoachSeason::new);
            cs.setCoachId(dto.id());
            cs.setSeason(s.year());
            cs.setSchool(s.school());
            cs.setTeamId(s.teamId() != null ? s.teamId() : teamIdFor(s.school()));
            cs.setConference(s.conference());
            cs.setGames(s.games());
            cs.setWins(s.wins());
            cs.setLosses(s.losses());
            cs.setTies(s.ties());
            cs.setSpOverall(s.spOverall());
            cs.setSpOffense(s.spOffense());
            cs.setSpDefense(s.spDefense());
            coachSeasons.save(cs);
        }
    }

    /**
     * The per-team coaches response can omit teamId. Resolving it by school
     * name is what keeps the school clickable on a coach's career table.
     */
    private Integer teamIdFor(String school) {
        return teams.findTop25BySchoolContainingIgnoreCaseOrderBySchoolAsc(school).stream()
                .filter(candidate -> candidate.getSchool().equalsIgnoreCase(school))
                .map(Team::getId)
                .findFirst()
                .orElse(null);
    }

    public boolean teamsSynced(int season) {
        return syncs.isSynced(RESOURCE_TEAMS, String.valueOf(season));
    }

    /**
     * Season win/loss splits for every team, in one API call. Full upsert
     * every time this is run, same as teams/coaches - an admin decides when
     * a fresh pull is worth it, there is no per-team staleness question.
     */
    @Transactional
    public int ingestRecords(int season) {
        List<CfbdDtos.RecordDto> dtos = cfbd.records(season);

        for (CfbdDtos.RecordDto dto : dtos) {
            if (dto.teamId() == null) {
                continue;
            }
            TeamRecord row = teamRecords.findByTeamIdAndSeason(dto.teamId(), season)
                    .orElseGet(TeamRecord::new);
            row.setTeamId(dto.teamId());
            row.setSeason(season);
            row.setClassification(dto.classification());
            row.setConference(dto.conference());
            row.setDivision(dto.division());
            row.setExpectedWins(dto.expectedWins());
            applySplits(row::setTotalGames, row::setTotalWins, row::setTotalLosses,
                    row::setTotalTies, dto.total());
            applySplits(row::setConferenceGames, row::setConferenceWins, row::setConferenceLosses,
                    row::setConferenceTies, dto.conferenceGames());
            applySplits(row::setHomeGames, row::setHomeWins, row::setHomeLosses,
                    row::setHomeTies, dto.homeGames());
            applySplits(row::setAwayGames, row::setAwayWins, row::setAwayLosses,
                    row::setAwayTies, dto.awayGames());
            applySplits(row::setNeutralGames, row::setNeutralWins, row::setNeutralLosses,
                    row::setNeutralTies, dto.neutralSiteGames());
            applySplits(row::setRegularGames, row::setRegularWins, row::setRegularLosses,
                    row::setRegularTies, dto.regularSeason());
            applySplits(row::setPostseasonGames, row::setPostseasonWins, row::setPostseasonLosses,
                    row::setPostseasonTies, dto.postseason());
            row.setUpdatedAt(Instant.now());
            teamRecords.save(row);
        }

        log.info("Ingested {} team records for {}", dtos.size(), season);
        return dtos.size();
    }

    private void applySplits(java.util.function.Consumer<Integer> games,
                             java.util.function.Consumer<Integer> wins,
                             java.util.function.Consumer<Integer> losses,
                             java.util.function.Consumer<Integer> ties,
                             CfbdDtos.RecordDto.Splits splits) {
        if (splits == null) {
            return;
        }
        games.accept(splits.games());
        wins.accept(splits.wins());
        losses.accept(splits.losses());
        ties.accept(splits.ties());
    }

    /**
     * CFBD returns logos ordered light, dark, light, dark ... at descending
     * sizes. Take the largest of the requested variant.
     */
    private String pickLogo(List<String> logos, boolean dark) {
        if (logos == null || logos.isEmpty()) {
            return null;
        }
        return logos.stream()
                .filter(url -> url.contains("logos-dark") == dark)
                .findFirst()
                .orElse(logos.get(0));
    }
}
