package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Integer> {

    List<Team> findAllByOrderBySchoolAsc();

    List<Team> findAllByConferenceIgnoreCaseOrderBySchoolAsc(String conference);

    List<Team> findTop25BySchoolContainingIgnoreCaseOrderBySchoolAsc(String fragment);
}
