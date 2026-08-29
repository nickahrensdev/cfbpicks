package com.nickspicks.api.ranking;

import com.nickspicks.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rules behind the rank shown beside a team's name: which poll wins,
 * and which week applies.
 */
class RankingServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private RankingService service;

    @Autowired
    private PollRankingRepository rankings;

    @Override
    protected void cleanUp() {
        rankings.deleteAll();
    }

    @Test
    void prefersTheCommitteeThenApThenCoaches() {
        // Week 5: no committee yet, so AP wins over coaches.
        rank(5, Poll.AP_TOP_25, 1, 100, "Texas A&M");
        rank(5, Poll.COACHES, 4, 100, "Texas A&M");

        // Week 12: the committee has started and outranks both.
        rank(12, Poll.PLAYOFF_COMMITTEE, 3, 100, "Texas A&M");
        rank(12, Poll.AP_TOP_25, 1, 100, "Texas A&M");
        rank(12, Poll.COACHES, 2, 100, "Texas A&M");

        assertThat(service.pollForWeek(2026, 5)).isEqualTo(Poll.AP_TOP_25);
        assertThat(service.rankLookup(2026, 5)).containsEntry(100, 1);

        assertThat(service.pollForWeek(2026, 12)).isEqualTo(Poll.PLAYOFF_COMMITTEE);
        assertThat(service.rankLookup(2026, 12)).containsEntry(100, 3);
    }

    @Test
    void fallsBackToTheMostRecentRankedWeek() {
        rank(3, Poll.AP_TOP_25, 7, 100, "Texas A&M");

        // Week 4 is live but its polls have not published yet - a reader still
        // expects "ranked going into this game", not a blank.
        assertThat(service.rankLookup(2026, 4)).containsEntry(100, 7);

        // Asking before any poll existed yields nothing rather than guessing.
        assertThat(service.rankLookup(2026, 2)).isEmpty();
    }

    @Test
    void currentRankUsesTheLatestWeekWhenNoWeekIsGiven() {
        rank(3, Poll.AP_TOP_25, 7, 100, "Texas A&M");
        rank(9, Poll.AP_TOP_25, 2, 100, "Texas A&M");

        assertThat(service.rankLookup(2026, null)).containsEntry(100, 2);
        // A week-scoped view still sees the older number.
        assertThat(service.rankLookup(2026, 3)).containsEntry(100, 7);
    }

    @Test
    void unrankedTeamsAreAbsentRatherThanZero() {
        rank(5, Poll.AP_TOP_25, 1, 100, "Texas A&M");

        Map<Integer, Integer> lookup = service.rankLookup(2026, 5);
        assertThat(lookup).containsOnlyKeys(100);
        assertThat(lookup.get(999)).isNull();
    }

    @Test
    void teamHistoryReturnsEveryPollNewestWeekFirst() {
        rank(5, Poll.AP_TOP_25, 8, 100, "Texas A&M");
        rank(6, Poll.AP_TOP_25, 6, 100, "Texas A&M");
        rank(6, Poll.COACHES, 7, 100, "Texas A&M");

        assertThat(service.teamHistory(2026, 100))
                .hasSize(3)
                .extracting(PollRanking::getWeek)
                .containsExactly(6, 6, 5);

        assertThat(service.currentPollsForTeam(2026, 100))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getWeek()).isEqualTo(6));
    }

    private void rank(int week, Poll poll, int rank, int teamId, String school) {
        PollRanking row = new PollRanking();
        row.setSeason(2026);
        row.setWeek(week);
        row.setSeasonType("regular");
        row.setPoll(poll.cfbdName());
        row.setRank(rank);
        row.setTeamId(teamId);
        row.setSchool(school);
        rankings.save(row);
    }
}
