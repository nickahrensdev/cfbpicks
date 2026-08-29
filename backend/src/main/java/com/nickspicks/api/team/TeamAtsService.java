package com.nickspicks.api.team;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.game.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Against-the-spread records, refreshed on demand rather than on a schedule.
 *
 * <p>Staleness is decided entirely from data already sitting in our own
 * {@code game} table - no CFBD call is ever spent just to find out whether a
 * refresh is needed. A cached row is good until a game that team played has
 * gone final more recently than the row was fetched; at that point the next
 * page view that asks for it triggers one bulk {@code /teams/ats} call, which
 * upserts every team's row, not just the one that was asked about. That is
 * what keeps the total call volume low under real traffic: whichever team's
 * page happens to be the first one viewed after any given Saturday refreshes
 * the whole league for everyone else's next view.
 */
@Service
public class TeamAtsService {

    private static final Logger log = LoggerFactory.getLogger(TeamAtsService.class);

    private final CfbdClient cfbd;
    private final TeamAtsRepository teamAts;
    private final GameRepository games;

    public TeamAtsService(CfbdClient cfbd, TeamAtsRepository teamAts, GameRepository games) {
        this.cfbd = cfbd;
        this.teamAts = teamAts;
        this.games = games;
    }

    /**
     * The current row for this team, refreshing the whole league first if
     * stale. Never throws - a provider hiccup degrades to whatever was
     * already cached (or null, if nothing has ever been fetched), the same
     * way {@code ensureRoster} degrades a team page rather than breaking it.
     */
    @Transactional
    public TeamAts ensureFresh(Integer teamId, int season) {
        TeamAts cached = teamAts.findByTeamIdAndSeason(teamId, season).orElse(null);

        if (!isStale(teamId, cached)) {
            return cached;
        }

        try {
            refreshAll(season);
        } catch (CfbdUnavailableException ex) {
            log.warn("Could not refresh team ATS for {}: {}", season, ex.getMessage());
            return cached;
        }

        return teamAts.findByTeamIdAndSeason(teamId, season).orElse(cached);
    }

    private boolean isStale(Integer teamId, TeamAts cached) {
        if (cached == null) {
            return true;
        }
        Instant lastFinal = games.findLastFinalAt(teamId);
        return lastFinal != null && lastFinal.isAfter(cached.getFetchedAt());
    }

    private void refreshAll(int season) {
        Instant now = Instant.now();
        for (CfbdDtos.AtsDto dto : cfbd.teamAts(season)) {
            if (dto.teamId() == null) {
                continue;
            }
            TeamAts row = teamAts.findByTeamIdAndSeason(dto.teamId(), season).orElseGet(TeamAts::new);
            row.setTeamId(dto.teamId());
            row.setSeason(season);
            row.setConference(dto.conference());
            row.setGames(dto.games());
            row.setAtsWins(dto.atsWins());
            row.setAtsLosses(dto.atsLosses());
            row.setAtsPushes(dto.atsPushes());
            row.setAvgCoverMargin(dto.avgCoverMargin());
            row.setFetchedAt(now);
            teamAts.save(row);
        }
        log.info("Refreshed team ATS for {}", season);
    }
}
