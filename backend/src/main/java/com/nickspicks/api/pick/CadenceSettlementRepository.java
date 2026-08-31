package com.nickspicks.api.pick;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface CadenceSettlementRepository
        extends JpaRepository<CadenceSettlement, CadenceSettlement.Key> {

    java.util.List<CadenceSettlement> findAllByGroupId(UUID groupId);

    /** The periods this group has already been charged for, as a lookup set. */
    default Set<String> settledKeys(UUID groupId) {
        return findAllByGroupId(groupId).stream()
                .map(CadenceSettlement::getPeriodKey)
                .collect(Collectors.toSet());
    }
}
