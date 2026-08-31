package com.nickspicks.api.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Key> {

    List<GroupMember> findAllByUserId(UUID userId);

    /** (userId, count) pairs, for the admin member list's "joined" column. */
    @org.springframework.data.jpa.repository.Query(
            "select m.userId, count(m) from GroupMember m group by m.userId")
    List<Object[]> countByMember();

    List<GroupMember> findAllByGroupId(UUID groupId);

    /** Used to check whether demoting or removing someone would leave no owner. */
    List<GroupMember> findAllByGroupIdAndRole(UUID groupId, GroupRole role);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupId(UUID groupId);

    /**
     * Member counts for a list of groups in one query - the group list and
     * search results both need a count per row.
     */
    @Query("select m.groupId, count(m) from GroupMember m where m.groupId in :groupIds group by m.groupId")
    List<Object[]> countByGroupIds(@Param("groupIds") Collection<UUID> groupIds);
}
