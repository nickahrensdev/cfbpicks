package com.nickspicks.api.leaderboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StandingsRepository extends JpaRepository<StandingsView, StandingsView.Key> {

    /** Ranked by total wins, then fewest losses. */
    @Query("""
            select s from StandingsView s
            where s.season = :season
            order by s.wins desc, s.losses asc, s.displayName asc
            """)
    List<StandingsView> leaderboard(@Param("season") Integer season);
}
