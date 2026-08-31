package com.nickspicks.api.pick;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CadencePenaltyRepository extends JpaRepository<CadencePenalty, UUID> {

    List<CadencePenalty> findAllByGroupIdAndPeriodKey(UUID groupId, String periodKey);

    List<CadencePenalty> findAllByGroupIdAndUserId(UUID groupId, UUID userId);

    /**
     * How many losses a member has been charged, across every period.
     *
     * <p>Elimination counts these alongside graded losses - missing a required
     * pick has to be able to knock someone out, or sitting a week would be the
     * safest play in a survivor pool.
     */
    @Query("""
            select coalesce(sum(p.shortfall), 0) from CadencePenalty p
            where p.groupId = :groupId and p.userId = :userId
            """)
    long totalShortfall(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    /**
     * The same, for one season. Every period key starts with its season -
     * '2026-W03' weekly, '2026-09-05' daily - so the year is a prefix match
     * either way.
     */
    @Query("""
            select coalesce(sum(p.shortfall), 0) from CadencePenalty p
            where p.groupId = :groupId and p.userId = :userId
              and p.periodKey like concat(:season, '%')
            """)
    long seasonShortfall(@Param("groupId") UUID groupId,
                         @Param("userId") UUID userId,
                         @Param("season") String season);
}
