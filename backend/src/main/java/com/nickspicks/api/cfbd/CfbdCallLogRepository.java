package com.nickspicks.api.cfbd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CfbdCallLogRepository extends JpaRepository<CfbdCallLog, Long> {

    @Query("select count(c) from CfbdCallLog c where c.calledAt >= :since")
    long countSince(@Param("since") Instant since);
}
