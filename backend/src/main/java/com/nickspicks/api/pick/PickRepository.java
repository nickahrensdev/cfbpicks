package com.nickspicks.api.pick;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickRepository extends JpaRepository<Pick, UUID> {

    Optional<Pick> findByGroupIdAndUserIdAndGameIdAndMarket(UUID groupId, UUID userId,
                                                            Long gameId, Market market);

    /** Every pick a member holds in a group, across every season. */
    List<Pick> findAllByGroupIdAndUserId(UUID groupId, UUID userId);

    /** A member's picks on one game in one group - at most one per market. */
    List<Pick> findAllByGroupIdAndUserIdAndGameId(UUID groupId, UUID userId, Long gameId);

    /** The caller's picks on a specific set of games - one query for a board. */
    List<Pick> findAllByGroupIdAndUserIdAndGameIdIn(UUID groupId, UUID userId,
                                                    java.util.Collection<Long> gameIds);

    /** Whether this member has any pick at all on this game in this group. */
    boolean existsByGroupIdAndUserIdAndGameId(UUID groupId, UUID userId, Long gameId);

    @Query("""
            select p from Pick p
            where p.groupId = :groupId and p.userId = :userId
              and p.gameId in (select g.id from Game g where g.season = :season and g.week = :week)
            """)
    List<Pick> findForUserWeek(@Param("groupId") UUID groupId,
                               @Param("userId") UUID userId,
                               @Param("season") Integer season,
                               @Param("week") Integer week);

    /**
     * A member's picks across a whole season.
     *
     * <p>A separate method rather than a nullable week on {@link
     * #findForUserWeek}: an {@code (:week is null or ...)} branch makes the
     * parameter's type ambiguous when it actually is null, and two plain
     * queries are cheaper to read than one clever one.
     */
    @Query("""
            select p from Pick p
            where p.groupId = :groupId and p.userId = :userId
              and p.gameId in (select g.id from Game g where g.season = :season)
            """)
    List<Pick> findForUserSeason(@Param("groupId") UUID groupId,
                                 @Param("userId") UUID userId,
                                 @Param("season") Integer season);

    /**
     * A member's picks on games that have not kicked off.
     *
     * <p>What an eliminated member is holding that can still be taken away.
     * Bounded by kickoff rather than by result: a pick on a game already
     * underway has been made and stands, whatever it finishes as.
     */
    @Query("""
            select p from Pick p
            where p.groupId = :groupId and p.userId = :userId
              and p.gameId in (select g.id from Game g where g.kickoff > :now)
            """)
    List<Pick> findUnstartedForUser(@Param("groupId") UUID groupId,
                                    @Param("userId") UUID userId,
                                    @Param("now") java.time.Instant now);

    /**
     * A member's picks on games kicking off in a window.
     *
     * <p>What a daily group's period looks like. A week has a column to filter
     * on; a day does not, so it has to be expressed as the span of instants
     * that day covers in the group-day timezone - see {@link CadencePeriod}.
     */
    @Query("""
            select p from Pick p
            where p.groupId = :groupId and p.userId = :userId
              and p.gameId in (select g.id from Game g
                               where g.kickoff >= :from and g.kickoff < :to)
            """)
    List<Pick> findForUserBetween(@Param("groupId") UUID groupId,
                                  @Param("userId") UUID userId,
                                  @Param("from") java.time.Instant from,
                                  @Param("to") java.time.Instant to);

    @Query("""
            select p from Pick p
            where p.groupId = :groupId
              and p.gameId in (select g.id from Game g where g.season = :season and g.week = :week)
            """)
    List<Pick> findAllForWeek(@Param("groupId") UUID groupId,
                              @Param("season") Integer season,
                              @Param("week") Integer week);

    /** Everyone's picks on one game within a group - the game detail reveal. */
    List<Pick> findAllByGroupIdAndGameId(UUID groupId, Long gameId);

    /** Every pick on a game across all groups - grading, which is group-blind. */
    List<Pick> findAllByGameId(Long gameId);

    List<Pick> findAllByUserId(UUID userId);

    /**
     * Graded losses this member has taken in a season - one half of an
     * elimination pool's strike count, the other being charged minimums.
     */
    @Query("""
            select count(p) from Pick p
            where p.groupId = :groupId and p.userId = :userId
              and p.result = com.nickspicks.api.pick.PickResult.LOSS
              and p.gameId in (select g.id from Game g where g.season = :season)
            """)
    long countLosses(@Param("groupId") UUID groupId,
                     @Param("userId") UUID userId,
                     @Param("season") Integer season);

    /** (userId, pick count) pairs for the admin user list, across every group. */
    @Query("select p.userId, count(p) from Pick p group by p.userId")
    List<Object[]> countByUser();
}
