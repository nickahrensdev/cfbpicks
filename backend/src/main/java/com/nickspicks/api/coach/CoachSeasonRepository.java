package com.nickspicks.api.coach;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachSeasonRepository extends JpaRepository<CoachSeason, CoachSeason.Key> {

    List<CoachSeason> findAllByCoachIdOrderBySeasonDesc(Integer coachId);

    List<CoachSeason> findAllByTeamIdAndSeason(Integer teamId, Integer season);
}
