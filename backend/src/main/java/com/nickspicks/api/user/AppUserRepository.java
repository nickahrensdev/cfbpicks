package com.nickspicks.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByDisplayNameIgnoreCase(String displayName);
}
