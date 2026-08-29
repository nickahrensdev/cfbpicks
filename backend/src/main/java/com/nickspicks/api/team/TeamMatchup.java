package com.nickspicks.api.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * All-time head-to-head history between two programs, cached and refreshed
 * on demand - see {@code TeamMatchupService} for the staleness rule.
 *
 * <p>{@code teamAId} is always the smaller of the two ids, {@code teamBId}
 * the larger - callers canonicalize before reading or writing, so a pairing
 * never ends up split across two rows depending on which team was asked
 * about first. {@code games} is the CFBD response's game list serialized as
 * JSON text: always read back as one whole list, never queried piecemeal, so
 * a child table or a jsonb column type buys nothing here.
 */
@Entity
@Table(name = "team_matchup", uniqueConstraints = @UniqueConstraint(columnNames = {"team_a_id", "team_b_id"}))
public class TeamMatchup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_a_id", nullable = false)
    private Integer teamAId;

    @Column(name = "team_b_id", nullable = false)
    private Integer teamBId;

    @Column(name = "team_a_wins")
    private Integer teamAWins;

    @Column(name = "team_b_wins")
    private Integer teamBWins;

    private Integer ties;

    @Column(nullable = false)
    private String games;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() {
        return id;
    }

    public Integer getTeamAId() {
        return teamAId;
    }

    public void setTeamAId(Integer teamAId) {
        this.teamAId = teamAId;
    }

    public Integer getTeamBId() {
        return teamBId;
    }

    public void setTeamBId(Integer teamBId) {
        this.teamBId = teamBId;
    }

    public Integer getTeamAWins() {
        return teamAWins;
    }

    public void setTeamAWins(Integer teamAWins) {
        this.teamAWins = teamAWins;
    }

    public Integer getTeamBWins() {
        return teamBWins;
    }

    public void setTeamBWins(Integer teamBWins) {
        this.teamBWins = teamBWins;
    }

    public Integer getTies() {
        return ties;
    }

    public void setTies(Integer ties) {
        this.ties = ties;
    }

    public String getGames() {
        return games;
    }

    public void setGames(String games) {
        this.games = games;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
