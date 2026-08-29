package com.nickspicks.api.pick;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickRepository extends JpaRepository<Pick, UUID> {

    Optional<Pick> findByUserIdAndGameId(UUID userId, Long gameId);

    Optional<Pick> findByUserIdAndGameIdAndMarket(UUID userId, Long gameId, Market market);

    /** A member's picks on one game - at most one per market. */
    List<Pick> findAllByUserIdAndGameId(UUID userId, Long gameId);

    @Query("""
            select p from Pick p
            where p.userId = :userId
              and p.gameId in (select g.id from Game g where g.season = :season and g.week = :week)
            """)
    List<Pick> findForUserWeek(@Param("userId") UUID userId,
                               @Param("season") Integer season,
                               @Param("week") Integer week);

    @Query("""
            select p from Pick p
            where p.gameId in (select g.id from Game g where g.season = :season and g.week = :week)
            """)
    List<Pick> findAllForWeek(@Param("season") Integer season, @Param("week") Integer week);

    List<Pick> findAllByGameId(Long gameId);

    List<Pick> findAllByUserId(UUID userId);

    /** (userId, pick count) pairs for the admin user list. */
    @Query("select p.userId, count(p) from Pick p group by p.userId")
    List<Object[]> countByUser();
}
