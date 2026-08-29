package com.nickspicks.api.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One team's against-the-spread record for a season, refreshed on demand -
 * see {@code TeamAtsService} for the staleness rule.
 */
@Entity
@Table(name = "team_ats", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "season"}))
public class TeamAts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(nullable = false)
    private Integer season;

    private String conference;
    private Integer games;

    @Column(name = "ats_wins")
    private Integer atsWins;

    @Column(name = "ats_losses")
    private Integer atsLosses;

    @Column(name = "ats_pushes")
    private Integer atsPushes;

    @Column(name = "avg_cover_margin")
    private BigDecimal avgCoverMargin;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() {
        return id;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public String getConference() {
        return conference;
    }

    public void setConference(String conference) {
        this.conference = conference;
    }

    public Integer getGames() {
        return games;
    }

    public void setGames(Integer games) {
        this.games = games;
    }

    public Integer getAtsWins() {
        return atsWins;
    }

    public void setAtsWins(Integer atsWins) {
        this.atsWins = atsWins;
    }

    public Integer getAtsLosses() {
        return atsLosses;
    }

    public void setAtsLosses(Integer atsLosses) {
        this.atsLosses = atsLosses;
    }

    public Integer getAtsPushes() {
        return atsPushes;
    }

    public void setAtsPushes(Integer atsPushes) {
        this.atsPushes = atsPushes;
    }

    public BigDecimal getAvgCoverMargin() {
        return avgCoverMargin;
    }

    public void setAvgCoverMargin(BigDecimal avgCoverMargin) {
        this.avgCoverMargin = avgCoverMargin;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
