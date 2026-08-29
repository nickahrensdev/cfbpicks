package com.nickspicks.api.pick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A member's slot count for one week.
 *
 * <p>This row exists to make the weekly pick cap atomic. Counting picks cannot
 * prevent two concurrent requests from both passing the check, because the
 * conflicting row does not exist yet and so {@code SELECT ... FOR UPDATE} has
 * nothing to lock. Every pick mutation takes a pessimistic write lock on this
 * row first, which serialises them.
 */
@Entity
@Table(name = "weekly_entry")
@IdClass(WeeklyEntry.Key.class)
public class WeeklyEntry {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    private Integer season;

    @Id
    private Integer week;

    @Column(name = "pick_count", nullable = false)
    private int pickCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WeeklyEntry() {
    }

    public WeeklyEntry(UUID userId, Integer season, Integer week) {
        this.userId = userId;
        this.season = season;
        this.week = week;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getSeason() {
        return season;
    }

    public Integer getWeek() {
        return week;
    }

    public int getPickCount() {
        return pickCount;
    }

    public void setPickCount(int pickCount) {
        this.pickCount = pickCount;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Composite key for {@link WeeklyEntry}. */
    public static class Key implements Serializable {

        private UUID userId;
        private Integer season;
        private Integer week;

        public Key() {
        }

        public Key(UUID userId, Integer season, Integer week) {
            this.userId = userId;
            this.season = season;
            this.week = week;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(season, key.season)
                    && Objects.equals(week, key.week);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, season, week);
        }
    }
}
