package com.nickspicks.api.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupShareLinkRepository extends JpaRepository<GroupShareLink, UUID> {

    Optional<GroupShareLink> findByGroupIdAndSharerId(UUID groupId, UUID sharerId);

    Optional<GroupShareLink> findByToken(String token);
}
