package com.nickspicks.api.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * All-time head-to-head history between two programs, refreshed on demand.
 *
 * <p>Freshness has two independent conditions, both checked against data we
 * already have before ever spending a CFBD call:
 * <ol>
 *   <li>the cached row was fetched this calendar year - the matchup endpoint
 *       has no season parameter of its own, it is simply "every game these
 *       two have ever played," so a fetch from last year is still correct,
 *       just possibly one game behind;
 *   <li>no game <em>between these two specific teams</em> has gone final
 *       since that fetch - the actual caveat that would make an otherwise
 *       "fresh enough" row wrong.
 * </ol>
 * Both are answered from our own {@code game} table.
 */
@Service
public class TeamMatchupService {

    private static final Logger log = LoggerFactory.getLogger(TeamMatchupService.class);

    private final CfbdClient cfbd;
    private final TeamMatchupRepository matchups;
    private final TeamRepository teams;
    private final GameRepository games;
    private final ObjectMapper json;

    public TeamMatchupService(CfbdClient cfbd, TeamMatchupRepository matchups, TeamRepository teams,
                              GameRepository games, ObjectMapper json) {
        this.cfbd = cfbd;
        this.matchups = matchups;
        this.teams = teams;
        this.games = games;
        this.json = json;
    }

    public record Matchup(Integer teamAId, Integer teamBId, Integer teamAWins, Integer teamBWins,
                          Integer ties, List<CfbdDtos.MatchupDto.MatchupGameDto> games) {
    }

    /** Never throws - a provider hiccup falls back to whatever was already cached, if anything. */
    @Transactional
    public Matchup ensureFresh(Integer teamId1, Integer teamId2) {
        Integer teamAId = Math.min(teamId1, teamId2);
        Integer teamBId = Math.max(teamId1, teamId2);

        TeamMatchup cached = matchups.findByTeamAIdAndTeamBId(teamAId, teamBId).orElse(null);

        if (!isStale(teamAId, teamBId, cached)) {
            return toMatchup(cached);
        }

        try {
            return toMatchup(refresh(teamAId, teamBId));
        } catch (CfbdUnavailableException ex) {
            log.warn("Could not refresh matchup {}/{}: {}", teamAId, teamBId, ex.getMessage());
            return cached == null ? null : toMatchup(cached);
        }
    }

    private boolean isStale(Integer teamAId, Integer teamBId, TeamMatchup cached) {
        if (cached == null) {
            return true;
        }
        int fetchedYear = cached.getFetchedAt().atZone(ZoneOffset.UTC).getYear();
        int currentYear = Instant.now().atZone(ZoneOffset.UTC).getYear();
        if (fetchedYear != currentYear) {
            return true;
        }
        Instant lastFinal = games.findLastFinalBetween(teamAId, teamBId);
        return lastFinal != null && lastFinal.isAfter(cached.getFetchedAt());
    }

    private TeamMatchup refresh(Integer teamAId, Integer teamBId) {
        String schoolA = schoolOf(teamAId);
        String schoolB = schoolOf(teamBId);

        CfbdDtos.MatchupDto dto = cfbd.matchup(schoolA, schoolB);

        TeamMatchup row = matchups.findByTeamAIdAndTeamBId(teamAId, teamBId).orElseGet(TeamMatchup::new);
        row.setTeamAId(teamAId);
        row.setTeamBId(teamBId);
        row.setTeamAWins(dto.team1Wins());
        row.setTeamBWins(dto.team2Wins());
        row.setTies(dto.ties());
        row.setGames(writeGames(dto.games()));
        row.setFetchedAt(Instant.now());

        TeamMatchup saved = matchups.save(row);
        log.info("Refreshed matchup {} vs {}", schoolA, schoolB);
        return saved;
    }

    private String schoolOf(Integer teamId) {
        return teams.findById(teamId)
                .map(Team::getSchool)
                .orElseThrow(() -> new NotFoundException("Team %d not found".formatted(teamId)));
    }

    private String writeGames(List<CfbdDtos.MatchupDto.MatchupGameDto> games) {
        try {
            return json.writeValueAsString(games == null ? List.of() : games);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize matchup games", ex);
        }
    }

    private List<CfbdDtos.MatchupDto.MatchupGameDto> readGames(String value) {
        try {
            return json.readValue(value,
                    json.getTypeFactory().constructCollectionType(List.class,
                            CfbdDtos.MatchupDto.MatchupGameDto.class));
        } catch (Exception ex) {
            log.warn("Could not deserialize cached matchup games, treating as empty: {}",
                    ex.getMessage());
            return List.of();
        }
    }

    private Matchup toMatchup(TeamMatchup row) {
        if (row == null) {
            return null;
        }
        return new Matchup(row.getTeamAId(), row.getTeamBId(), row.getTeamAWins(),
                row.getTeamBWins(), row.getTies(), readGames(row.getGames()));
    }
}
