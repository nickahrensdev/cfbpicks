package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamAtsRepository extends JpaRepository<TeamAts, Long> {

    Optional<TeamAts> findByTeamIdAndSeason(Integer teamId, Integer season);
}
