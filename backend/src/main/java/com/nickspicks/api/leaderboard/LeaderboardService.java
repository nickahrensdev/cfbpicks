package com.nickspicks.api.leaderboard;

import com.nickspicks.api.web.ApiDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Standings for the leaderboard page.
 *
 * <p>Starts from app_user rather than pick, so every member appears - a
 * 0-0 row with zero picks is a valid standing, not an absence. The optional
 * week narrows both the record and the pick count to that week; without it
 * the whole season counts.
 */
@Service
public class LeaderboardService {

    private static final String SQL = """
            select u.id,
                   u.display_name,
                   coalesce(s.total, 0)  as total_picks,
                   coalesce(s.wins, 0)   as wins,
                   coalesce(s.losses, 0) as losses,
                   coalesce(s.pushes, 0) as pushes
            from app_user u
            left join (
                select p.user_id,
                       count(*)                                    as total,
                       count(*) filter (where p.result = 'WIN')    as wins,
                       count(*) filter (where p.result = 'LOSS')   as losses,
                       count(*) filter (where p.result = 'PUSH')   as pushes
                from pick p
                join game g on g.id = p.game_id
                where g.season = ?
                  and (?::int is null or g.week = ?::int)
                group by p.user_id
            ) s on s.user_id = u.id
            order by coalesce(s.wins, 0) desc,
                     coalesce(s.losses, 0) asc,
                     lower(u.display_name)
            """;

    private final JdbcTemplate jdbc;

    public LeaderboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ApiDtos.StandingsRow> standings(int season, Integer week) {
        List<Row> rows = jdbc.query(SQL,
                (rs, i) -> new Row(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("display_name"),
                        rs.getLong("total_picks"),
                        rs.getLong("wins"),
                        rs.getLong("losses"),
                        rs.getLong("pushes")),
                season, week, week);

        List<ApiDtos.StandingsRow> ranked = new ArrayList<>(rows.size());
        int rank = 0;
        long previousWins = -1;
        long previousLosses = -1;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);

            // Standard competition ranking: identical records share a rank,
            // and the next distinct record skips ahead.
            if (row.wins != previousWins || row.losses != previousLosses) {
                rank = i + 1;
                previousWins = row.wins;
                previousLosses = row.losses;
            }

            long decided = row.wins + row.losses;
            Double winPct = decided == 0 ? null : (double) row.wins / decided;

            ranked.add(new ApiDtos.StandingsRow(rank, row.userId, row.displayName,
                    row.totalPicks, row.wins, row.losses, row.pushes, winPct));
        }
        return ranked;
    }

    private record Row(UUID userId, String displayName, long totalPicks,
                       long wins, long losses, long pushes) {
    }
}
