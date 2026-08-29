package com.nickspicks.api.pick;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickAuditRepository extends JpaRepository<PickAudit, Long> {

    List<PickAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PickAudit> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
