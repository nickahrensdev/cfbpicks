package com.nickspicks.api.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A scheduled game. The id is CFBD's, so repeated ingests upsert rather than
 * duplicate.
 */
@Entity
@Table(name = "game")
public class Game {

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer season;

    @Column(nullable = false)
    private Integer week;

    @Column(name = "season_type", nullable = false)
    private String seasonType = "regular";

    @Column(name = "home_team_id")
    private Integer homeTeamId;

    @Column(name = "home_team", nullable = false)
    private String homeTeam;

    @Column(name = "home_conference")
    private String homeConference;

    @Column(name = "away_team_id")
    private Integer awayTeamId;

    @Column(name = "away_team", nullable = false)
    private String awayTeam;

    @Column(name = "away_conference")
    private String awayConference;

    @Column(name = "neutral_site", nullable = false)
    private boolean neutralSite;

    @Column(name = "conference_game", nullable = false)
    private boolean conferenceGame;

    private String venue;

    @Column(nullable = false)
    private Instant kickoff;

    /** True when the kickoff time has not been announced. Not pickable. */
    @Column(name = "start_time_tbd", nullable = false)
    private boolean startTimeTbd;

    /** From the home team's perspective: -7.5 means home favored by 7.5. */
    @Column(name = "home_spread")
    private BigDecimal homeSpread;

    @Column(name = "spread_open")
    private BigDecimal spreadOpen;

    @Column(name = "over_under")
    private BigDecimal overUnder;

    @Column(name = "over_under_open")
    private BigDecimal overUnderOpen;

    @Column(name = "home_moneyline")
    private Integer homeMoneyline;

    @Column(name = "away_moneyline")
    private Integer awayMoneyline;

    @Column(name = "spread_provider")
    private String spreadProvider;

    @Column(name = "spread_updated_at")
    private Instant spreadUpdatedAt;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "home_pregame_elo")
    private Integer homePregameElo;

    @Column(name = "away_pregame_elo")
    private Integer awayPregameElo;

    /** Postgame win probability, 0..1. Null until the game finishes. */
    @Column(name = "home_postgame_win_probability")
    private BigDecimal homePostgameWinProbability;

    @Column(name = "away_postgame_win_probability")
    private BigDecimal awayPostgameWinProbability;

    @Column(name = "excitement_index")
    private BigDecimal excitementIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status = GameStatus.SCHEDULED;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public String getSeasonType() {
        return seasonType;
    }

    public void setSeasonType(String seasonType) {
        this.seasonType = seasonType;
    }

    public Integer getHomeTeamId() {
        return homeTeamId;
    }

    public void setHomeTeamId(Integer homeTeamId) {
        this.homeTeamId = homeTeamId;
    }

    public String getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
    }

    public String getHomeConference() {
        return homeConference;
    }

    public void setHomeConference(String homeConference) {
        this.homeConference = homeConference;
    }

    public Integer getAwayTeamId() {
        return awayTeamId;
    }

    public void setAwayTeamId(Integer awayTeamId) {
        this.awayTeamId = awayTeamId;
    }

    public String getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
    }

    public String getAwayConference() {
        return awayConference;
    }

    public void setAwayConference(String awayConference) {
        this.awayConference = awayConference;
    }

    public boolean isNeutralSite() {
        return neutralSite;
    }

    public void setNeutralSite(boolean neutralSite) {
        this.neutralSite = neutralSite;
    }

    public boolean isConferenceGame() {
        return conferenceGame;
    }

    public void setConferenceGame(boolean conferenceGame) {
        this.conferenceGame = conferenceGame;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public Instant getKickoff() {
        return kickoff;
    }

    public void setKickoff(Instant kickoff) {
        this.kickoff = kickoff;
    }

    public boolean isStartTimeTbd() {
        return startTimeTbd;
    }

    public void setStartTimeTbd(boolean startTimeTbd) {
        this.startTimeTbd = startTimeTbd;
    }

    public BigDecimal getHomeSpread() {
        return homeSpread;
    }

    public void setHomeSpread(BigDecimal homeSpread) {
        this.homeSpread = homeSpread;
    }

    public BigDecimal getSpreadOpen() {
        return spreadOpen;
    }

    public void setSpreadOpen(BigDecimal spreadOpen) {
        this.spreadOpen = spreadOpen;
    }

    public BigDecimal getOverUnder() {
        return overUnder;
    }

    public void setOverUnder(BigDecimal overUnder) {
        this.overUnder = overUnder;
    }

    public BigDecimal getOverUnderOpen() {
        return overUnderOpen;
    }

    public void setOverUnderOpen(BigDecimal overUnderOpen) {
        this.overUnderOpen = overUnderOpen;
    }

    public Integer getHomeMoneyline() {
        return homeMoneyline;
    }

    public void setHomeMoneyline(Integer homeMoneyline) {
        this.homeMoneyline = homeMoneyline;
    }

    public Integer getAwayMoneyline() {
        return awayMoneyline;
    }

    public void setAwayMoneyline(Integer awayMoneyline) {
        this.awayMoneyline = awayMoneyline;
    }

    public String getSpreadProvider() {
        return spreadProvider;
    }

    public void setSpreadProvider(String spreadProvider) {
        this.spreadProvider = spreadProvider;
    }

    public Instant getSpreadUpdatedAt() {
        return spreadUpdatedAt;
    }

    public void setSpreadUpdatedAt(Instant spreadUpdatedAt) {
        this.spreadUpdatedAt = spreadUpdatedAt;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public void setHomeScore(Integer homeScore) {
        this.homeScore = homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }

    public void setAwayScore(Integer awayScore) {
        this.awayScore = awayScore;
    }

    public Integer getHomePregameElo() {
        return homePregameElo;
    }

    public void setHomePregameElo(Integer homePregameElo) {
        this.homePregameElo = homePregameElo;
    }

    public Integer getAwayPregameElo() {
        return awayPregameElo;
    }

    public void setAwayPregameElo(Integer awayPregameElo) {
        this.awayPregameElo = awayPregameElo;
    }

    public BigDecimal getHomePostgameWinProbability() {
        return homePostgameWinProbability;
    }

    public void setHomePostgameWinProbability(BigDecimal homePostgameWinProbability) {
        this.homePostgameWinProbability = homePostgameWinProbability;
    }

    public BigDecimal getAwayPostgameWinProbability() {
        return awayPostgameWinProbability;
    }

    public void setAwayPostgameWinProbability(BigDecimal awayPostgameWinProbability) {
        this.awayPostgameWinProbability = awayPostgameWinProbability;
    }

    public BigDecimal getExcitementIndex() {
        return excitementIndex;
    }

    public void setExcitementIndex(BigDecimal excitementIndex) {
        this.excitementIndex = excitementIndex;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
