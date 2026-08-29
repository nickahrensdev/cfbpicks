package com.nickspicks.api.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamAtsRepository extends JpaRepository<TeamAts, Long> {

    Optional<TeamAts> findByTeamIdAndSeason(Integer teamId, Integer season);

    /** The whole season at once, so a refresh does not look up a row per team. */
    List<TeamAts> findAllBySeason(Integer season);

    /** Every season this team has an ATS record for, newest first. */
    List<TeamAts> findAllByTeamIdOrderBySeasonDesc(Integer teamId);

    /** Every season for either of two teams, newest first - one query for a matchup. */
    List<TeamAts> findAllByTeamIdInOrderBySeasonDesc(Collection<Integer> teamIds);
}
