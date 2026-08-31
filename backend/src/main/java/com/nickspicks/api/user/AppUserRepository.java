package com.nickspicks.api.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Uniqueness moved to the username; display names may repeat. */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Name or email contains the term, case-insensitively.
     *
     * <p>For pickers that used to render every account at once. That works
     * until it does not: the list grows with the site, and a select holding
     * every member is unusable long before it is slow.
     */
    @Query("""
            select u from AppUser u
            where lower(u.displayName) like lower(concat('%', :term, '%'))
               or lower(u.username) like lower(concat('%', :term, '%'))
               or lower(u.email) like lower(concat('%', :term, '%'))
            order by u.username
            """)
    List<AppUser> search(@Param("term") String term, Pageable pageable);
}
