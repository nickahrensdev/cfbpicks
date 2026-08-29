package com.nickspicks.api.coach;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** One season at one school - the rows behind a coach's career table. */
@Entity
@Table(name = "coach_season")
@IdClass(CoachSeason.Key.class)
public class CoachSeason {

    @Id
    @Column(name = "coach_id")
    private Integer coachId;

    @Id
    private Integer season;

    @Id
    private String school;

    @Column(name = "team_id")
    private Integer teamId;

    private String conference;
    private Integer games;
    private Integer wins;
    private Integer losses;
    private Integer ties;

    @Column(name = "sp_overall")
    private BigDecimal spOverall;

    @Column(name = "sp_offense")
    private BigDecimal spOffense;

    @Column(name = "sp_defense")
    private BigDecimal spDefense;

    public Integer getCoachId() {
        return coachId;
    }

    public void setCoachId(Integer coachId) {
        this.coachId = coachId;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
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

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getTies() {
        return ties;
    }

    public void setTies(Integer ties) {
        this.ties = ties;
    }

    public BigDecimal getSpOverall() {
        return spOverall;
    }

    public void setSpOverall(BigDecimal spOverall) {
        this.spOverall = spOverall;
    }

    public BigDecimal getSpOffense() {
        return spOffense;
    }

    public void setSpOffense(BigDecimal spOffense) {
        this.spOffense = spOffense;
    }

    public BigDecimal getSpDefense() {
        return spDefense;
    }

    public void setSpDefense(BigDecimal spDefense) {
        this.spDefense = spDefense;
    }

    /** Composite key for {@link CoachSeason}. */
    public static class Key implements Serializable {

        private Integer coachId;
        private Integer season;
        private String school;

        public Key() {
        }

        public Key(Integer coachId, Integer season, String school) {
            this.coachId = coachId;
            this.season = season;
            this.school = school;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(coachId, key.coachId)
                    && Objects.equals(season, key.season)
                    && Objects.equals(school, key.school);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coachId, season, school);
        }
    }
}
