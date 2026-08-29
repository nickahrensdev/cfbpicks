package com.nickspicks.api.athlete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A roster entry. CFBD keys athletes by a string id, and a player appears once
 * per season they were rostered, so the key is (id, season).
 */
@Entity
@Table(name = "athlete")
@IdClass(Athlete.Key.class)
public class Athlete {

    @Id
    private String id;

    @Id
    private Integer season;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "team_school")
    private String teamSchool;

    private String position;
    private Integer jersey;

    /** Inches. */
    private Integer height;

    /** Pounds. */
    private Integer weight;

    /** Class year: 1 = freshman .. 5. */
    private Integer year;

    @Column(name = "home_city")
    private String homeCity;

    @Column(name = "home_state")
    private String homeState;

    @Column(name = "home_country")
    private String homeCountry;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public String getTeamSchool() {
        return teamSchool;
    }

    public void setTeamSchool(String teamSchool) {
        this.teamSchool = teamSchool;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getJersey() {
        return jersey;
    }

    public void setJersey(Integer jersey) {
        this.jersey = jersey;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getHomeCity() {
        return homeCity;
    }

    public void setHomeCity(String homeCity) {
        this.homeCity = homeCity;
    }

    public String getHomeState() {
        return homeState;
    }

    public void setHomeState(String homeState) {
        this.homeState = homeState;
    }

    public String getHomeCountry() {
        return homeCountry;
    }

    public void setHomeCountry(String homeCountry) {
        this.homeCountry = homeCountry;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Composite key for {@link Athlete}. */
    public static class Key implements Serializable {

        private String id;
        private Integer season;

        public Key() {
        }

        public Key(String id, Integer season) {
            this.id = id;
            this.season = season;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(id, key.id)
                    && Objects.equals(season, key.season);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, season);
        }
    }
}
