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
 * One team's season win/loss splits - overall, conference, home/away/neutral,
 * regular season vs. postseason. Admin-triggered, refreshed wholesale like
 * teams/coaches; there is no per-team staleness question here.
 */
@Entity
@Table(name = "team_record", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "season"}))
public class TeamRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(nullable = false)
    private Integer season;

    private String classification;
    private String conference;
    private String division;

    @Column(name = "expected_wins")
    private BigDecimal expectedWins;

    @Column(name = "total_games")
    private Integer totalGames;
    @Column(name = "total_wins")
    private Integer totalWins;
    @Column(name = "total_losses")
    private Integer totalLosses;
    @Column(name = "total_ties")
    private Integer totalTies;

    @Column(name = "conference_games")
    private Integer conferenceGames;
    @Column(name = "conference_wins")
    private Integer conferenceWins;
    @Column(name = "conference_losses")
    private Integer conferenceLosses;
    @Column(name = "conference_ties")
    private Integer conferenceTies;

    @Column(name = "home_games")
    private Integer homeGames;
    @Column(name = "home_wins")
    private Integer homeWins;
    @Column(name = "home_losses")
    private Integer homeLosses;
    @Column(name = "home_ties")
    private Integer homeTies;

    @Column(name = "away_games")
    private Integer awayGames;
    @Column(name = "away_wins")
    private Integer awayWins;
    @Column(name = "away_losses")
    private Integer awayLosses;
    @Column(name = "away_ties")
    private Integer awayTies;

    @Column(name = "neutral_games")
    private Integer neutralGames;
    @Column(name = "neutral_wins")
    private Integer neutralWins;
    @Column(name = "neutral_losses")
    private Integer neutralLosses;
    @Column(name = "neutral_ties")
    private Integer neutralTies;

    @Column(name = "regular_games")
    private Integer regularGames;
    @Column(name = "regular_wins")
    private Integer regularWins;
    @Column(name = "regular_losses")
    private Integer regularLosses;
    @Column(name = "regular_ties")
    private Integer regularTies;

    @Column(name = "postseason_games")
    private Integer postseasonGames;
    @Column(name = "postseason_wins")
    private Integer postseasonWins;
    @Column(name = "postseason_losses")
    private Integer postseasonLosses;
    @Column(name = "postseason_ties")
    private Integer postseasonTies;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getConference() {
        return conference;
    }

    public void setConference(String conference) {
        this.conference = conference;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public BigDecimal getExpectedWins() {
        return expectedWins;
    }

    public void setExpectedWins(BigDecimal expectedWins) {
        this.expectedWins = expectedWins;
    }

    public Integer getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(Integer totalGames) {
        this.totalGames = totalGames;
    }

    public Integer getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(Integer totalWins) {
        this.totalWins = totalWins;
    }

    public Integer getTotalLosses() {
        return totalLosses;
    }

    public void setTotalLosses(Integer totalLosses) {
        this.totalLosses = totalLosses;
    }

    public Integer getTotalTies() {
        return totalTies;
    }

    public void setTotalTies(Integer totalTies) {
        this.totalTies = totalTies;
    }

    public Integer getConferenceGames() {
        return conferenceGames;
    }

    public void setConferenceGames(Integer conferenceGames) {
        this.conferenceGames = conferenceGames;
    }

    public Integer getConferenceWins() {
        return conferenceWins;
    }

    public void setConferenceWins(Integer conferenceWins) {
        this.conferenceWins = conferenceWins;
    }

    public Integer getConferenceLosses() {
        return conferenceLosses;
    }

    public void setConferenceLosses(Integer conferenceLosses) {
        this.conferenceLosses = conferenceLosses;
    }

    public Integer getConferenceTies() {
        return conferenceTies;
    }

    public void setConferenceTies(Integer conferenceTies) {
        this.conferenceTies = conferenceTies;
    }

    public Integer getHomeGames() {
        return homeGames;
    }

    public void setHomeGames(Integer homeGames) {
        this.homeGames = homeGames;
    }

    public Integer getHomeWins() {
        return homeWins;
    }

    public void setHomeWins(Integer homeWins) {
        this.homeWins = homeWins;
    }

    public Integer getHomeLosses() {
        return homeLosses;
    }

    public void setHomeLosses(Integer homeLosses) {
        this.homeLosses = homeLosses;
    }

    public Integer getHomeTies() {
        return homeTies;
    }

    public void setHomeTies(Integer homeTies) {
        this.homeTies = homeTies;
    }

    public Integer getAwayGames() {
        return awayGames;
    }

    public void setAwayGames(Integer awayGames) {
        this.awayGames = awayGames;
    }

    public Integer getAwayWins() {
        return awayWins;
    }

    public void setAwayWins(Integer awayWins) {
        this.awayWins = awayWins;
    }

    public Integer getAwayLosses() {
        return awayLosses;
    }

    public void setAwayLosses(Integer awayLosses) {
        this.awayLosses = awayLosses;
    }

    public Integer getAwayTies() {
        return awayTies;
    }

    public void setAwayTies(Integer awayTies) {
        this.awayTies = awayTies;
    }

    public Integer getNeutralGames() {
        return neutralGames;
    }

    public void setNeutralGames(Integer neutralGames) {
        this.neutralGames = neutralGames;
    }

    public Integer getNeutralWins() {
        return neutralWins;
    }

    public void setNeutralWins(Integer neutralWins) {
        this.neutralWins = neutralWins;
    }

    public Integer getNeutralLosses() {
        return neutralLosses;
    }

    public void setNeutralLosses(Integer neutralLosses) {
        this.neutralLosses = neutralLosses;
    }

    public Integer getNeutralTies() {
        return neutralTies;
    }

    public void setNeutralTies(Integer neutralTies) {
        this.neutralTies = neutralTies;
    }

    public Integer getRegularGames() {
        return regularGames;
    }

    public void setRegularGames(Integer regularGames) {
        this.regularGames = regularGames;
    }

    public Integer getRegularWins() {
        return regularWins;
    }

    public void setRegularWins(Integer regularWins) {
        this.regularWins = regularWins;
    }

    public Integer getRegularLosses() {
        return regularLosses;
    }

    public void setRegularLosses(Integer regularLosses) {
        this.regularLosses = regularLosses;
    }

    public Integer getRegularTies() {
        return regularTies;
    }

    public void setRegularTies(Integer regularTies) {
        this.regularTies = regularTies;
    }

    public Integer getPostseasonGames() {
        return postseasonGames;
    }

    public void setPostseasonGames(Integer postseasonGames) {
        this.postseasonGames = postseasonGames;
    }

    public Integer getPostseasonWins() {
        return postseasonWins;
    }

    public void setPostseasonWins(Integer postseasonWins) {
        this.postseasonWins = postseasonWins;
    }

    public Integer getPostseasonLosses() {
        return postseasonLosses;
    }

    public void setPostseasonLosses(Integer postseasonLosses) {
        this.postseasonLosses = postseasonLosses;
    }

    public Integer getPostseasonTies() {
        return postseasonTies;
    }

    public void setPostseasonTies(Integer postseasonTies) {
        this.postseasonTies = postseasonTies;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
