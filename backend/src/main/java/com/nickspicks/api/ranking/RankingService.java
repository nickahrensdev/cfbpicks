package com.nickspicks.api.ranking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the single rank shown beside a team's name.
 *
 * <p>Two decisions live here:
 *
 * <ul>
 *   <li><b>Which week.</b> Polls are published during a week, so week 4 can be
 *       live before week 4's rankings exist. The lookup falls back to the most
 *       recent ranked week at or before the one asked for, which is what a
 *       reader means by "ranked going into this game".
 *   <li><b>Which poll.</b> The highest-priority poll that actually published
 *       that week - committee, then AP, then coaches. The committee only
 *       starts around week 11, so earlier weeks fall through to AP.
 * </ul>
 *
 * <p>Picking one poll per week is deliberate: showing whichever poll each
 * screen happened to query would let the same team read as #3 in one place and
 * #5 in another.
 */
@Service
public class RankingService {

    private static final String REGULAR = "regular";

    private final PollRankingRepository rankings;

    public RankingService(PollRankingRepository rankings) {
        this.rankings = rankings;
    }

    /**
     * team id → rank, for the poll and week that apply. Empty when nothing has
     * been ingested, which is the normal state before the season starts.
     *
     * @param week the week being viewed, or null for "current"
     */
    @Transactional(readOnly = true)
    public Map<Integer, Integer> rankLookup(int season, Integer week) {
        Integer effectiveWeek = week == null
                ? rankings.findLatestRankedWeek(season, REGULAR)
                : rankings.findLatestRankedWeekUpTo(season, REGULAR, week);

        if (effectiveWeek == null) {
            return Map.of();
        }

        Poll poll = pollForWeek(season, effectiveWeek);
        if (poll == null) {
            return Map.of();
        }

        Map<Integer, Integer> byTeam = new HashMap<>();
        rankings.findAllBySeasonAndSeasonTypeAndWeekAndPoll(season, REGULAR, effectiveWeek,
                        poll.cfbdName())
                .forEach(row -> {
                    if (row.getTeamId() != null) {
                        byTeam.put(row.getTeamId(), row.getRank());
                    }
                });
        return byTeam;
    }

    /** The highest-priority poll that published in a given week. */
    @Transactional(readOnly = true)
    public Poll pollForWeek(int season, int week) {
        List<String> published = rankings.findPollsForWeek(season, REGULAR, week);
        return Poll.priorityOrder().stream()
                .filter(poll -> published.contains(poll.cfbdName()))
                .findFirst()
                .orElse(null);
    }

    /** Every stored poll entry for a team this season, newest week first. */
    @Transactional(readOnly = true)
    public List<PollRanking> teamHistory(int season, int teamId) {
        return rankings.findTeamHistory(season, teamId);
    }

    /**
     * All three polls' current positions for one team, for the team page. Uses
     * the latest ranked week regardless of which polls published.
     */
    @Transactional(readOnly = true)
    public List<PollRanking> currentPollsForTeam(int season, int teamId) {
        Integer latest = rankings.findLatestRankedWeek(season, REGULAR);
        if (latest == null) {
            return List.of();
        }
        return rankings.findTeamHistory(season, teamId).stream()
                .filter(row -> latest.equals(row.getWeek()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Integer latestRankedWeek(int season) {
        return rankings.findLatestRankedWeek(season, REGULAR);
    }
}
