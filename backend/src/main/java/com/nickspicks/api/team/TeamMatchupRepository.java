package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamMatchupRepository extends JpaRepository<TeamMatchup, Long> {

    Optional<TeamMatchup> findByTeamAIdAndTeamBId(Integer teamAId, Integer teamBId);
}
