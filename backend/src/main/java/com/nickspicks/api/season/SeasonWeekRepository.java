package com.nickspicks.api.season;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonWeekRepository extends JpaRepository<SeasonWeek, SeasonWeek.Key> {

    List<SeasonWeek> findAllBySeasonAndSeasonTypeOrderByWeekAsc(Integer season, String seasonType);
}
