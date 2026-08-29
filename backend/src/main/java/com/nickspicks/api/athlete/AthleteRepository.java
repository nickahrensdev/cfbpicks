package com.nickspicks.api.athlete;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AthleteRepository extends JpaRepository<Athlete, Athlete.Key> {

    List<Athlete> findAllByTeamIdAndSeasonOrderByJerseyAsc(Integer teamId, Integer season);

    List<Athlete> findAllByIdOrderBySeasonDesc(String id);

    Optional<Athlete> findFirstByIdOrderBySeasonDesc(String id);

    boolean existsByTeamIdAndSeason(Integer teamId, Integer season);
}
