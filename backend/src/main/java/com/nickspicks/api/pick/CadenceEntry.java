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
 * A member's pick count for one period of one group.
 *
 * <p>This row exists to make the pick cap atomic. Counting picks cannot
 * prevent two concurrent requests from both passing the check, because the
 * conflicting row does not exist yet and so {@code SELECT ... FOR UPDATE} has
 * nothing to lock. Every pick mutation takes a pessimistic write lock on this
 * row first, which serialises them.
 *
 * <p>Replaces {@code weekly_entry}, whose key hard-coded the week as the
 * counting period. The period is now an opaque label from
 * {@link CadencePeriod}, so a daily group counts by day without a second
 * mechanism.
 */
@Entity
@Table(name = "cadence_entry")
@IdClass(CadenceEntry.Key.class)
public class CadenceEntry {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "period_key")
    private String periodKey;

    @Column(name = "pick_count", nullable = false)
    private int pickCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CadenceEntry() {
    }

    public CadenceEntry(UUID groupId, UUID userId, String periodKey) {
        this.groupId = groupId;
        this.userId = userId;
        this.periodKey = periodKey;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPeriodKey() {
        return periodKey;
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

    /** Composite key for {@link CadenceEntry}. */
    public static class Key implements Serializable {

        private UUID groupId;
        private UUID userId;
        private String periodKey;

        public Key() {
        }

        public Key(UUID groupId, UUID userId, String periodKey) {
            this.groupId = groupId;
            this.userId = userId;
            this.periodKey = periodKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(groupId, key.groupId)
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(periodKey, key.periodKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId, periodKey);
        }
    }
}
