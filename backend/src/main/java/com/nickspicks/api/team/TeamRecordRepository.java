package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRecordRepository extends JpaRepository<TeamRecord, Long> {

    Optional<TeamRecord> findByTeamIdAndSeason(Integer teamId, Integer season);
}
