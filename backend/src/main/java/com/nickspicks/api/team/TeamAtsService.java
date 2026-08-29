package com.nickspicks.api.team;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Against-the-spread records. Reads are pure database lookups - nothing on a
 * page-request path ever calls CollegeFootballData.
 *
 * <p>This used to refresh on demand, keyed off whether the asked-for team had
 * a row yet. That could never terminate: {@code /teams/ats} only returns teams
 * that have actually played, so a team the provider has no data for stayed
 * row-less no matter how many times the league was re-fetched, and every view
 * of a game involving one spent two 10-second-timeout calls re-learning that.
 * Refreshing is now an explicit admin action instead - see
 * {@code AdminIngestController}'s ATS load, which runs in the background like
 * every other manual load.
 */
@Service
public class TeamAtsService {

    private static final Logger log = LoggerFactory.getLogger(TeamAtsService.class);

    private final CfbdClient cfbd;
    private final TeamAtsRepository teamAts;

    public TeamAtsService(CfbdClient cfbd, TeamAtsRepository teamAts) {
        this.cfbd = cfbd;
        this.teamAts = teamAts;
    }

    /**
     * This team's stored record, or null when there is none - which is the
     * normal case for a team that has not played yet, not an error. Never
     * calls the provider.
     */
    @Transactional(readOnly = true)
    public TeamAts find(Integer teamId, int season) {
        if (teamId == null) {
            return null;
        }
        return teamAts.findByTeamIdAndSeason(teamId, season).orElse(null);
    }

    /**
     * Pulls every team's record for the season in one API call and upserts
     * the lot. Throws {@link com.nickspicks.api.cfbd.CfbdUnavailableException}
     * if the provider is unreachable, so the caller records a failure rather
     * than reporting a load that did nothing.
     *
     * @return how many rows were written
     */
    @Transactional
    public int refreshSeason(int season) {
        Instant now = Instant.now();

        // One read for the whole season rather than a lookup per team - this
        // upserts a few hundred rows and the round trips add up.
        Map<Integer, TeamAts> existing = teamAts.findAllBySeason(season).stream()
                .collect(Collectors.toMap(TeamAts::getTeamId, Function.identity(), (a, b) -> a));

        List<TeamAts> updated = new ArrayList<>();
        for (CfbdDtos.AtsDto dto : cfbd.teamAts(season)) {
            if (dto.teamId() == null) {
                continue;
            }
            TeamAts row = existing.getOrDefault(dto.teamId(), new TeamAts());
            row.setTeamId(dto.teamId());
            row.setSeason(season);
            row.setConference(dto.conference());
            row.setGames(dto.games());
            row.setAtsWins(dto.atsWins());
            row.setAtsLosses(dto.atsLosses());
            row.setAtsPushes(dto.atsPushes());
            row.setAvgCoverMargin(dto.avgCoverMargin());
            row.setFetchedAt(now);
            updated.add(row);
        }

        teamAts.saveAll(updated);
        log.info("Refreshed team ATS for {}: {} rows", season, updated.size());
        return updated.size();
    }
}
