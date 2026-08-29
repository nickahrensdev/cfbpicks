package com.nickspicks.api.team;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The whole point of {@link TeamAtsService} is spending a CFBD call only when
 * one is actually warranted - these pin down exactly when that is, using a
 * mocked {@link GameRepository} standing in for "has a game of theirs
 * concluded since we last asked".
 */
class TeamAtsServiceTest {

    private static final int SEASON = 2026;

    private CfbdClient cfbd;
    private TeamAtsRepository repo;
    private GameRepository games;
    private TeamAtsService service;

    private void setUp() {
        cfbd = mock(CfbdClient.class);
        repo = mock(TeamAtsRepository.class);
        games = mock(GameRepository.class);
        service = new TeamAtsService(cfbd, repo, games);
    }

    @Test
    void fetchesWhenNothingIsCachedYet() {
        setUp();
        when(repo.findByTeamIdAndSeason(333, SEASON)).thenReturn(Optional.empty());
        when(games.findLastFinalAt(333)).thenReturn(null);
        when(cfbd.teamAts(SEASON)).thenReturn(List.of(atsDto(333)));

        service.ensureFresh(333, SEASON);

        verify(cfbd).teamAts(SEASON);
    }

    @Test
    void skipsTheCallWhenCachedRowIsStillCurrent() {
        setUp();
        TeamAts cached = atsRow(333, Instant.now().minus(1, ChronoUnit.HOURS));
        when(repo.findByTeamIdAndSeason(333, SEASON)).thenReturn(Optional.of(cached));
        // Their last final game concluded before the cache was written.
        when(games.findLastFinalAt(333)).thenReturn(cached.getFetchedAt().minus(1, ChronoUnit.DAYS));

        TeamAts result = service.ensureFresh(333, SEASON);

        verify(cfbd, never()).teamAts(anyInt());
        assertThat(result).isSameAs(cached);
    }

    @Test
    void refetchesOnceAGameHasConcludedSinceTheCacheWasWritten() {
        setUp();
        TeamAts cached = atsRow(333, Instant.now().minus(1, ChronoUnit.DAYS));
        when(repo.findByTeamIdAndSeason(333, SEASON))
                .thenReturn(Optional.of(cached))
                .thenReturn(Optional.of(atsRow(333, Instant.now())));
        // Their game concluded AFTER the cached row was fetched.
        when(games.findLastFinalAt(333)).thenReturn(Instant.now().minus(1, ChronoUnit.HOURS));
        when(cfbd.teamAts(SEASON)).thenReturn(List.of(atsDto(333), atsDto(334)));

        service.ensureFresh(333, SEASON);

        verify(cfbd).teamAts(SEASON);
    }

    @Test
    void aRefreshUpsertsEveryTeamNotJustTheOneAsked() {
        setUp();
        when(repo.findByTeamIdAndSeason(any(), any())).thenReturn(Optional.empty());
        when(games.findLastFinalAt(any())).thenReturn(null);
        when(cfbd.teamAts(SEASON)).thenReturn(List.of(atsDto(333), atsDto(334), atsDto(335)));

        service.ensureFresh(333, SEASON);

        verify(repo).save(argThatTeamId(333));
        verify(repo).save(argThatTeamId(334));
        verify(repo).save(argThatTeamId(335));
    }

    @Test
    void aProviderFailureFallsBackToWhateverWasCachedRatherThanThrowing() {
        setUp();
        TeamAts cached = atsRow(333, Instant.now().minus(30, ChronoUnit.DAYS));
        when(repo.findByTeamIdAndSeason(333, SEASON)).thenReturn(Optional.of(cached));
        when(games.findLastFinalAt(333)).thenReturn(Instant.now());
        when(cfbd.teamAts(SEASON)).thenThrow(new CfbdUnavailableException("down"));

        TeamAts result = service.ensureFresh(333, SEASON);

        assertThat(result).isSameAs(cached);
    }

    private TeamAts argThatTeamId(int teamId) {
        return org.mockito.ArgumentMatchers.argThat(row -> row != null
                && teamId == (row.getTeamId() == null ? -1 : row.getTeamId()));
    }

    private TeamAts atsRow(int teamId, Instant fetchedAt) {
        TeamAts row = new TeamAts();
        row.setTeamId(teamId);
        row.setSeason(SEASON);
        row.setFetchedAt(fetchedAt);
        return row;
    }

    private CfbdDtos.AtsDto atsDto(int teamId) {
        return new CfbdDtos.AtsDto(SEASON, teamId, "Team " + teamId, "SEC", 5, 3, 2, 0,
                java.math.BigDecimal.ONE);
    }
}
