package com.nickspicks.api.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GroupReferralRepository extends JpaRepository<GroupReferral, UUID> {

    long countBySharerId(UUID sharerId);

    /** (sharerId, count) pairs, for the admin member list's referral column. */
    @Query("select r.sharerId, count(r) from GroupReferral r group by r.sharerId")
    List<Object[]> countBySharer();
}
