package com.nickspicks.api.team;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdDtos;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reads must never reach the provider. This used to refresh on demand keyed
 * off whether the asked-for team had a row, which could not terminate:
 * {@code /teams/ats} only returns teams that have played, so a team it has no
 * data for stayed row-less however many times the league was re-fetched, and
 * every game-details view burned two 10-second-timeout calls re-learning it.
 * {@link TeamAtsService#find} making zero provider calls is the regression
 * these guard.
 */
class TeamAtsServiceTest {

    private static final int SEASON = 2026;

    private CfbdClient cfbd;
    private TeamAtsRepository repo;
    private TeamAtsService service;

    private void setUp() {
        cfbd = mock(CfbdClient.class);
        repo = mock(TeamAtsRepository.class);
        service = new TeamAtsService(cfbd, repo);
    }

    @Test
    void findReturnsTheStoredRowWithoutCallingTheProvider() {
        setUp();
        TeamAts stored = row(333, 7, 3);
        when(repo.findByTeamIdAndSeason(333, SEASON)).thenReturn(Optional.of(stored));

        assertThat(service.find(333, SEASON)).isSameAs(stored);
        verifyNoInteractions(cfbd);
    }

    /**
     * The case that caused the bug: a team the provider has no ATS data for
     * yet. Null is the correct answer and must stay cheap - fetching here is
     * what looped forever, since the fetch could never produce the row.
     */
    @Test
    void findReturnsNullForATeamWithNoRowWithoutCallingTheProvider() {
        setUp();
        when(repo.findByTeamIdAndSeason(333, SEASON)).thenReturn(Optional.empty());

        assertThat(service.find(333, SEASON)).isNull();
        verifyNoInteractions(cfbd);
    }

    @Test
    void findToleratesATeamWithNoIdAtAll() {
        setUp();

        assertThat(service.find(null, SEASON)).isNull();
        verifyNoInteractions(cfbd);
    }

    @Test
    void refreshSeasonUpsertsEveryTeamTheProviderReturns() {
        setUp();
        // 333 already has a row and must be updated in place rather than
        // duplicated; 444 is new.
        TeamAts existing = row(333, 1, 1);
        when(repo.findAllBySeason(SEASON)).thenReturn(List.of(existing));
        when(cfbd.teamAts(SEASON)).thenReturn(List.of(ats(333, 7, 3), ats(444, 2, 8)));

        assertThat(service.refreshSeason(SEASON)).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TeamAts>> saved = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(saved.capture());

        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue().get(0)).isSameAs(existing);
        assertThat(existing.getAtsWins()).isEqualTo(7);
        assertThat(existing.getAtsLosses()).isEqualTo(3);
        assertThat(saved.getValue().get(1).getTeamId()).isEqualTo(444);
    }

    /** A row with no team id cannot be keyed, so it is skipped rather than saved. */
    @Test
    void refreshSeasonSkipsRowsWithNoTeamId() {
        setUp();
        when(repo.findAllBySeason(SEASON)).thenReturn(List.of());
        when(cfbd.teamAts(SEASON)).thenReturn(List.of(ats(null, 1, 0), ats(444, 2, 8)));

        assertThat(service.refreshSeason(SEASON)).isEqualTo(1);
    }

    /**
     * Propagated, not swallowed - the background job that calls this records a
     * FAILURE row, which would otherwise report a load that stored nothing as
     * a success.
     */
    @Test
    void refreshSeasonPropagatesAProviderOutage() {
        setUp();
        when(repo.findAllBySeason(SEASON)).thenReturn(List.of());
        when(cfbd.teamAts(anyInt())).thenThrow(new CfbdUnavailableException("down"));

        assertThatThrownBy(() -> service.refreshSeason(SEASON))
                .isInstanceOf(CfbdUnavailableException.class);
    }

    // ------------------------------------------------------------- fixtures

    private TeamAts row(int teamId, int wins, int losses) {
        TeamAts row = new TeamAts();
        row.setTeamId(teamId);
        row.setSeason(SEASON);
        row.setAtsWins(wins);
        row.setAtsLosses(losses);
        row.setFetchedAt(Instant.now());
        return row;
    }

    private CfbdDtos.AtsDto ats(Integer teamId, int wins, int losses) {
        return new CfbdDtos.AtsDto(SEASON, teamId, "Team " + teamId, "Conf",
                wins + losses, wins, losses, 0, new BigDecimal("1.5"));
    }
}
