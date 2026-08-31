package com.nickspicks.api.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, UUID> {

    Optional<GroupJoinRequest> findByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupJoinRequest> findAllByGroupIdOrderByRequestedAtAsc(UUID groupId);

    long countByGroupIdAndStatus(UUID groupId, JoinRequestStatus status);

    /** Pending counts for a list of groups, for the badge on each group row. */
    @Query("""
            select r.groupId, count(r) from GroupJoinRequest r
            where r.groupId in :groupIds and r.status = :status
            group by r.groupId
            """)
    List<Object[]> countByGroupIds(@Param("groupIds") Collection<UUID> groupIds,
                                   @Param("status") JoinRequestStatus status);
}
