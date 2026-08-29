package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamAtsRepository extends JpaRepository<TeamAts, Long> {

    Optional<TeamAts> findByTeamIdAndSeason(Integer teamId, Integer season);

    /** The whole season at once, so a refresh does not look up a row per team. */
    List<TeamAts> findAllBySeason(Integer season);
}
