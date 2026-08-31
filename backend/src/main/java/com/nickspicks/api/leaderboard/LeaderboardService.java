package com.nickspicks.api.leaderboard;

import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupType;
import com.nickspicks.api.pick.CadencePeriod;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Standings for one group, and the only place ranking is decided.
 *
 * <p>Starts from group_member rather than pick, so every member of the group
 * appears - a 0-0-0 row with zero picks is a valid standing, not an absence.
 * It also means the board contains the league and nobody else, which starting
 * from app_user would not.
 *
 * <p>Scoring is per group. Rather than the old fixed "win 1, tie 0.5, loss 0",
 * the SQL returns a win/loss/push count <em>per market</em> and Java multiplies
 * those by the group's configured point values. Doing the arithmetic in Java
 * keeps one copy of it and lets the group's numbers stay parameters rather
 * than becoming nine more bind variables inside an ORDER BY.
 *
 * <p>Ranked by points, then most wins, then fewest losses. With configurable
 * scoring, points alone no longer imply a record: a group that pays 2 for a
 * spread win and 1 for a total win can have two members level on points with
 * quite different cards, so the wins and losses tiebreaks do real work.
 */
@Service
public class LeaderboardService {

    /**
     * Season and week are both optional. A continuous group has no season
     * filter at all; a per-year group passes one. The {@code ?::int is null}
     * form lets a single statement serve every combination.
     */
    private static final String SQL = """
            select u.id,
                   u.display_name,
                   u.username,
                   coalesce(s.total, 0)          as total_picks,
                   coalesce(s.spread_wins, 0)    as spread_wins,
                   coalesce(s.spread_losses, 0)  as spread_losses,
                   coalesce(s.spread_pushes, 0)  as spread_pushes,
                   coalesce(s.total_wins, 0)     as total_wins,
                   coalesce(s.total_losses, 0)   as total_losses,
                   coalesce(s.total_pushes, 0)   as total_pushes,
                   coalesce(s.winner_wins, 0)    as winner_wins,
                   coalesce(s.winner_losses, 0)  as winner_losses,
                   coalesce(s.winner_pushes, 0)  as winner_pushes,
                   coalesce(c.penalty_losses, 0) as penalty_losses,
                   coalesce(c.penalty_points, 0) as penalty_points
            from group_member gm
            join app_user u on u.id = gm.user_id
            left join (
                select p.user_id,
                       count(*) as total,
                       count(*) filter (where p.result = 'WIN'  and p.market = 'SPREAD') as spread_wins,
                       count(*) filter (where p.result = 'LOSS' and p.market = 'SPREAD') as spread_losses,
                       count(*) filter (where p.result = 'PUSH' and p.market = 'SPREAD') as spread_pushes,
                       count(*) filter (where p.result = 'WIN'  and p.market = 'TOTAL')  as total_wins,
                       count(*) filter (where p.result = 'LOSS' and p.market = 'TOTAL')  as total_losses,
                       count(*) filter (where p.result = 'PUSH' and p.market = 'TOTAL')  as total_pushes,
                       count(*) filter (where p.result = 'WIN'  and p.market = 'WINNER') as winner_wins,
                       count(*) filter (where p.result = 'LOSS' and p.market = 'WINNER') as winner_losses,
                       count(*) filter (where p.result = 'PUSH' and p.market = 'WINNER') as winner_pushes
                from pick p
                join game g on g.id = p.game_id
                where p.group_id = ?
                  and (?::int is null or g.season = ?::int)
                  and (?::int is null or g.week = ?::int)
                group by p.user_id
            ) s on s.user_id = u.id
            left join (
                select cp.user_id,
                       sum(cp.shortfall) as penalty_losses,
                       sum(cp.points)    as penalty_points
                from cadence_penalty cp
                where cp.group_id = ?
                  and (?::text is null or cp.period_key = ?::text)
                  and (?::text is null or left(cp.period_key, 4) = ?::text)
                group by cp.user_id
            ) c on c.user_id = u.id
            where gm.group_id = ?
            """;

    private final JdbcTemplate jdbc;

    public LeaderboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param season null for every season, which is what a continuous group's
     *               all-time board means
     * @param week   null for the whole season
     */
    public List<ApiDtos.StandingsRow> standings(Group group, Integer season, Integer week) {
        UUID groupId = group.getId();

        // Penalties are filed under a period key rather than a season and week,
        // so the same two filters have to be expressed against that key. A
        // single week only names a period for a weekly group - a week of a
        // daily group spans seven of them, and there is no one key to match.
        String periodKey = week != null && season != null && group.getCadence() == Cadence.WEEKLY
                ? CadencePeriod.weekly(season, week)
                : null;
        // Every key starts with its season, weekly ('2026-W03') and daily
        // ('2026-09-05') alike, so the year is a prefix match either way.
        String seasonText = season == null ? null : String.valueOf(season);

        List<Row> rows = jdbc.query(SQL,
                (rs, i) -> new Row(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("display_name"),
                        rs.getString("username"),
                        rs.getLong("total_picks"),
                        rs.getLong("spread_wins"),
                        rs.getLong("spread_losses"),
                        rs.getLong("spread_pushes"),
                        rs.getLong("total_wins"),
                        rs.getLong("total_losses"),
                        rs.getLong("total_pushes"),
                        rs.getLong("winner_wins"),
                        rs.getLong("winner_losses"),
                        rs.getLong("winner_pushes"),
                        rs.getLong("penalty_losses"),
                        rs.getBigDecimal("penalty_points")),
                groupId, season, season, week, week,
                groupId, periodKey, periodKey, seasonText, seasonText,
                groupId);

        record Scored(Row row, double points, long wins, long losses, long pushes) {
        }

        List<Scored> scored = rows.stream()
                // A charged minimum counts as a loss, in the record and in the
                // points alike - which is the whole point of charging it.
                .map(row -> new Scored(row, points(group, row),
                        row.spreadWins + row.totalWins + row.winnerWins,
                        row.spreadLosses + row.totalLosses + row.winnerLosses + row.penaltyLosses,
                        row.spreadPushes + row.totalPushes + row.winnerPushes))
                .sorted(Comparator
                        .comparingDouble((Scored s) -> s.points).reversed()
                        .thenComparing(Comparator.comparingLong((Scored s) -> s.wins).reversed())
                        .thenComparingLong(s -> s.losses)
                        .thenComparing(s -> s.row.displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<ApiDtos.StandingsRow> ranked = new ArrayList<>(scored.size());
        int rank = 0;
        double previousPoints = Double.NaN;
        long previousWins = -1;
        long previousLosses = -1;

        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);

            // Standard competition ranking: identical records share a rank,
            // and the next distinct record skips ahead. Compares the whole
            // sort tuple, not just the points - two members level on points
            // but split by the wins tiebreak are ranked apart, not together.
            if (s.points != previousPoints
                    || s.wins != previousWins
                    || s.losses != previousLosses) {
                rank = i + 1;
                previousPoints = s.points;
                previousWins = s.wins;
                previousLosses = s.losses;
            }

            // Ties sit out of the denominator, so this stays "of the picks
            // that were decided, how many did they win".
            long decided = s.wins + s.losses;
            Double winPct = decided == 0 ? null : (double) s.wins / decided;

            ranked.add(new ApiDtos.StandingsRow(rank, s.row.userId, s.row.displayName,
                    s.row.username, s.row.totalPicks, s.wins, s.losses, s.pushes, s.points, winPct,
                    s.row.penaltyLosses, isEliminated(group, s.losses)));
        }
        return ranked;
    }

    /**
     * Whether this record puts a member out of an elimination pool.
     *
     * <p>Derived rather than stored, so it can never disagree with the record
     * beside it - an eliminated flag written once and a loss corrected later
     * would be two sources of truth for the same fact.
     *
     * <p>"Two strikes allowed" means the third one ends it, so the test is
     * strictly greater than. Charged minimums are already in this count, which
     * they have to be: otherwise sitting out a week would be the safest play in
     * a pool whose whole premise is that you cannot sit out.
     */
    private boolean isEliminated(Group group, long losses) {
        return group.getGroupType() == GroupType.ELIMINATION
                && group.getStrikesAllowed() != null
                && losses > group.getStrikesAllowed();
    }

    /** The group's own scoring, applied per market. */
    private double points(Group group, Row row) {
        BigDecimal total = BigDecimal.ZERO
                .add(group.getSpreadWinPoints().multiply(BigDecimal.valueOf(row.spreadWins)))
                .add(group.getSpreadLossPoints().multiply(BigDecimal.valueOf(row.spreadLosses)))
                .add(group.getSpreadPushPoints().multiply(BigDecimal.valueOf(row.spreadPushes)))
                .add(group.getTotalWinPoints().multiply(BigDecimal.valueOf(row.totalWins)))
                .add(group.getTotalLossPoints().multiply(BigDecimal.valueOf(row.totalLosses)))
                .add(group.getTotalPushPoints().multiply(BigDecimal.valueOf(row.totalPushes)))
                .add(group.getWinnerWinPoints().multiply(BigDecimal.valueOf(row.winnerWins)))
                .add(group.getWinnerLossPoints().multiply(BigDecimal.valueOf(row.winnerLosses)))
                .add(group.getWinnerPushPoints().multiply(BigDecimal.valueOf(row.winnerPushes)))
                // Already money, priced by the group's scoring when the period
                // was settled - not re-derived, so changing the point values
                // now cannot rewrite what a closed period cost.
                .add(row.penaltyPoints == null ? BigDecimal.ZERO : row.penaltyPoints);
        return total.doubleValue();
    }

    private record Row(UUID userId, String displayName, String username, long totalPicks,
                       long spreadWins, long spreadLosses, long spreadPushes,
                       long totalWins, long totalLosses, long totalPushes,
                       long winnerWins, long winnerLosses, long winnerPushes,
                       /** Minimums this member finished short of - see CadenceSettlementService. */
                       long penaltyLosses, BigDecimal penaltyPoints) {
    }
}
