package com.nickspicks.api.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRankingRepository extends JpaRepository<PollRanking, Long> {

    Optional<PollRanking> findBySeasonAndWeekAndSeasonTypeAndPollAndSchool(
            Integer season, Integer week, String seasonType, String poll, String school);

    List<PollRanking> findAllBySeasonAndSeasonTypeAndWeekAndPoll(
            Integer season, String seasonType, Integer week, String poll);

    /** Every poll's entries for a team in a season, newest week first. */
    @Query("""
            select r from PollRanking r
            where r.season = :season and r.teamId = :teamId
            order by r.week desc, r.poll asc
            """)
    List<PollRanking> findTeamHistory(@Param("season") Integer season,
                                      @Param("teamId") Integer teamId);

    /**
     * Which polls have entries for a week, so the caller can pick the
     * highest-priority one that actually published.
     */
    @Query("""
            select distinct r.poll from PollRanking r
            where r.season = :season and r.seasonType = :seasonType and r.week = :week
            """)
    List<String> findPollsForWeek(@Param("season") Integer season,
                                  @Param("seasonType") String seasonType,
                                  @Param("week") Integer week);

    /**
     * The most recent week at or before the one asked for that has any
     * rankings. Polls are published during a week, so a week can be live
     * before its rankings exist.
     */
    @Query("""
            select max(r.week) from PollRanking r
            where r.season = :season and r.seasonType = :seasonType and r.week <= :week
            """)
    Integer findLatestRankedWeekUpTo(@Param("season") Integer season,
                                     @Param("seasonType") String seasonType,
                                     @Param("week") Integer week);

    @Query("""
            select max(r.week) from PollRanking r
            where r.season = :season and r.seasonType = :seasonType
            """)
    Integer findLatestRankedWeek(@Param("season") Integer season,
                                 @Param("seasonType") String seasonType);
}
