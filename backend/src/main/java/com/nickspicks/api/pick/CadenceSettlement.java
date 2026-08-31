package com.nickspicks.api.pick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A period this group has been closed out for.
 *
 * <p>Its only job is to make settlement idempotent. The settling job looks at
 * every period whose games have all kicked off, and this is how it knows which
 * of them it has already charged - without it, a job that runs every hour would
 * charge the same missed minimum again on every pass.
 */
@Entity
@Table(name = "cadence_settlement")
@IdClass(CadenceSettlement.Key.class)
public class CadenceSettlement {

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Id
    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt = Instant.now();

    protected CadenceSettlement() {
    }

    public CadenceSettlement(UUID groupId, String periodKey) {
        this.groupId = groupId;
        this.periodKey = periodKey;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public record Key(UUID groupId, String periodKey) implements Serializable {

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(groupId, key.groupId)
                    && Objects.equals(periodKey, key.periodKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, periodKey);
        }
    }
}
