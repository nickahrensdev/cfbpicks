package com.nickspicks.api.game;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllBySeasonAndWeekOrderByKickoffAsc(Integer season, Integer week);

    @Query("""
            select g from Game g
            where g.season = :season
              and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)
            order by g.week asc
            """)
    List<Game> findSeasonScheduleForTeam(@Param("season") Integer season,
                                         @Param("teamId") Integer teamId);

    /**
     * Games that could plausibly be underway. The score poller calls this
     * before hitting CFBD, so a day with no football costs zero API calls.
     */
    @Query("""
            select g from Game g
            where g.status <> com.nickspicks.api.game.GameStatus.FINAL
              and g.status <> com.nickspicks.api.game.GameStatus.CANCELED
              and g.kickoff <= :now
              and g.kickoff >= :staleBefore
            """)
    List<Game> findPotentiallyLive(@Param("now") Instant now,
                                   @Param("staleBefore") Instant staleBefore);

    @Query("select distinct g.week from Game g where g.season = :season order by g.week asc")
    List<Integer> findWeeks(@Param("season") Integer season);

    /**
     * The moment we last knew this team had a game conclude - the signal
     * {@code TeamAtsService} uses to decide whether a cached ATS row is
     * stale, without spending any CFBD quota to find out.
     */
    @Query("""
            select max(g.updatedAt) from Game g
            where g.status = com.nickspicks.api.game.GameStatus.FINAL
              and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)
            """)
    Instant findLastFinalAt(@Param("teamId") Integer teamId);

    /** Same as {@link #findLastFinalAt}, but for games between these two teams specifically. */
    @Query("""
            select max(g.updatedAt) from Game g
            where g.status = com.nickspicks.api.game.GameStatus.FINAL
              and ((g.homeTeamId = :teamAId and g.awayTeamId = :teamBId)
                or (g.homeTeamId = :teamBId and g.awayTeamId = :teamAId))
            """)
    Instant findLastFinalBetween(@Param("teamAId") Integer teamAId, @Param("teamBId") Integer teamBId);

    @Query("""
            select min(g.kickoff) from Game g
            where g.season = :season and g.week = :week
            """)
    Instant findFirstKickoff(@Param("season") Integer season, @Param("week") Integer week);
}
