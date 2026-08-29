package com.nickspicks.api.season;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** A week from the CFBD calendar, ingested once per season. */
@Entity
@Table(name = "season_week")
@IdClass(SeasonWeek.Key.class)
public class SeasonWeek {

    @Id
    private Integer season;

    @Id
    private Integer week;

    @Id
    @Column(name = "season_type")
    private String seasonType = "regular";

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "first_game_start")
    private Instant firstGameStart;

    @Column(name = "last_game_start")
    private Instant lastGameStart;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Instant getFirstGameStart() {
        return firstGameStart;
    }

    public void setFirstGameStart(Instant firstGameStart) {
        this.firstGameStart = firstGameStart;
    }

    public Instant getLastGameStart() {
        return lastGameStart;
    }

    public void setLastGameStart(Instant lastGameStart) {
        this.lastGameStart = lastGameStart;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Composite key for {@link SeasonWeek}. */
    public static class Key implements Serializable {

        private Integer season;
        private Integer week;
        private String seasonType;

        public Key() {
        }

        public Key(Integer season, Integer week, String seasonType) {
            this.season = season;
            this.week = week;
            this.seasonType = seasonType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(season, key.season)
                    && Objects.equals(week, key.week)
                    && Objects.equals(seasonType, key.seasonType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(season, week, seasonType);
        }
    }
}
