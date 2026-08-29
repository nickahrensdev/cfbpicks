package com.nickspicks.api.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.game.GameRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TeamMatchupService} has two independent freshness conditions - a
 * calendar-year check (the endpoint has no season parameter of its own) and
 * "has a game between these two specific teams concluded since the last
 * fetch" - both answered from our own data before ever spending a call.
 */
class TeamMatchupServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    private CfbdClient cfbd;
    private TeamMatchupRepository repo;
    private TeamRepository teams;
    private GameRepository games;
    private TeamMatchupService service;

    private void setUp() {
        cfbd = mock(CfbdClient.class);
        repo = mock(TeamMatchupRepository.class);
        teams = mock(TeamRepository.class);
        games = mock(GameRepository.class);
        service = new TeamMatchupService(cfbd, repo, teams, games, json);

        when(teams.findById(333)).thenReturn(Optional.of(school(333, "Alabama")));
        when(teams.findById(2)).thenReturn(Optional.of(school(2, "Auburn")));
    }

    @Test
    void fetchesWhenNothingIsCachedYet() {
        setUp();
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.empty());
        when(cfbd.matchup("Auburn", "Alabama")).thenReturn(matchupDto());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureFresh(333, 2);

        verify(cfbd).matchup("Auburn", "Alabama");
    }

    @Test
    void canonicalizesOrderSoEitherCallOrderHitsTheSameRow() {
        setUp();
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.empty());
        when(cfbd.matchup(any(), any())).thenReturn(matchupDto());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureFresh(333, 2);
        service.ensureFresh(2, 333);

        // Both calls resolve to team_a=2 (Auburn, smaller id), team_b=333 -
        // one row's worth of lookups regardless of argument order (each
        // ensureFresh looks the row up twice: once to check staleness, once
        // inside refresh() to upsert it).
        verify(repo, org.mockito.Mockito.times(4)).findByTeamAIdAndTeamBId(2, 333);
        verify(repo, never()).findByTeamAIdAndTeamBId(333, 2);
    }

    @Test
    void skipsTheCallWhenFetchedThisYearAndNoNewMeetingHasConcluded() {
        setUp();
        TeamMatchup cached = row(Instant.now().minus(1, ChronoUnit.HOURS));
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.of(cached));
        when(games.findLastFinalBetween(2, 333)).thenReturn(cached.getFetchedAt().minus(1, ChronoUnit.DAYS));

        service.ensureFresh(333, 2);

        verify(cfbd, never()).matchup(any(), any());
    }

    @Test
    void refetchesWhenTheseTwoTeamsHavePlayedSinceTheCacheWasWritten() {
        setUp();
        TeamMatchup cached = row(Instant.now().minus(1, ChronoUnit.DAYS));
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.of(cached));
        // They played each other AFTER the cache was written.
        when(games.findLastFinalBetween(2, 333)).thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(cfbd.matchup(any(), any())).thenReturn(matchupDto());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureFresh(333, 2);

        verify(cfbd).matchup("Auburn", "Alabama");
    }

    @Test
    void refetchesWhenTheCacheIsFromAPriorCalendarYearEvenWithNoNewMeeting() {
        setUp();
        // Fetched over a year ago; the two have not played since (the
        // year-turnover rule fires independently of that fact).
        TeamMatchup cached = row(Instant.now().minus(400, ChronoUnit.DAYS));
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.of(cached));
        when(games.findLastFinalBetween(2, 333)).thenReturn(null);
        when(cfbd.matchup(any(), any())).thenReturn(matchupDto());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureFresh(333, 2);

        verify(cfbd).matchup("Auburn", "Alabama");
    }

    @Test
    void aProviderFailureFallsBackToWhateverWasCachedRatherThanThrowing() {
        setUp();
        TeamMatchup cached = row(Instant.now().minus(400, ChronoUnit.DAYS));
        when(repo.findByTeamAIdAndTeamBId(2, 333)).thenReturn(Optional.of(cached));
        when(games.findLastFinalBetween(2, 333)).thenReturn(null);
        when(cfbd.matchup(any(), any())).thenThrow(new CfbdUnavailableException("down"));

        TeamMatchupService.Matchup result = service.ensureFresh(333, 2);

        assertThat(result).isNotNull();
        assertThat(result.teamAId()).isEqualTo(2);
    }

    private Team school(int id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setSchool(name);
        return team;
    }

    private TeamMatchup row(Instant fetchedAt) {
        TeamMatchup row = new TeamMatchup();
        row.setTeamAId(2);
        row.setTeamBId(333);
        row.setTeamAWins(10);
        row.setTeamBWins(20);
        row.setTies(0);
        row.setGames("[]");
        row.setFetchedAt(fetchedAt);
        return row;
    }

    private CfbdDtos.MatchupDto matchupDto() {
        return new CfbdDtos.MatchupDto("Auburn", "Alabama", 10, 20, 0, List.of());
    }
}
