package com.nickspicks.api.pick;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WeeklyEntryRepository extends JpaRepository<WeeklyEntry, WeeklyEntry.Key> {

    /**
     * Pessimistic write lock - this is what serialises concurrent pick
     * mutations for the same member and week.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from WeeklyEntry e
            where e.userId = :userId and e.season = :season and e.week = :week
            """)
    Optional<WeeklyEntry> findAndLock(@Param("userId") UUID userId,
                                      @Param("season") Integer season,
                                      @Param("week") Integer week);
}
