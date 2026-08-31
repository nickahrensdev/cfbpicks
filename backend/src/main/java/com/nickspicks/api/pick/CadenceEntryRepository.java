package com.nickspicks.api.pick;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CadenceEntryRepository extends JpaRepository<CadenceEntry, CadenceEntry.Key> {

    /**
     * Pessimistic write lock - this is what serialises concurrent pick
     * mutations for the same member and period.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from CadenceEntry e
            where e.groupId = :groupId and e.userId = :userId and e.periodKey = :periodKey
            """)
    Optional<CadenceEntry> findAndLock(@Param("groupId") UUID groupId,
                                       @Param("userId") UUID userId,
                                       @Param("periodKey") String periodKey);
}
