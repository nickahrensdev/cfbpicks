package com.nickspicks.api.pick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A loss charged for a minimum a member did not meet.
 *
 * <p>Separate from {@link Pick} because it is a loss with no pick behind it -
 * no game, no line, nothing to grade. Putting it in the pick table would mean
 * every query that joins a pick to its game having to allow for a row that has
 * none.
 *
 * <p>The cost is stored rather than derived. A group that changes its point
 * values in October must not silently rewrite September's standings, and a
 * settled period is history.
 */
@Entity
@Table(name = "cadence_penalty")
public class CadencePenalty {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    /** Null for the group's overall minimum, which names no market. */
    @Enumerated(EnumType.STRING)
    @Column(name = "market", length = 8)
    private Market market;

    @Column(name = "shortfall", nullable = false)
    private int shortfall;

    @Column(name = "points", nullable = false)
    private BigDecimal points;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CadencePenalty() {
    }

    public CadencePenalty(UUID groupId, UUID userId, String periodKey, Market market,
                          int shortfall, BigDecimal points) {
        this.groupId = groupId;
        this.userId = userId;
        this.periodKey = periodKey;
        this.market = market;
        this.shortfall = shortfall;
        this.points = points;
    }

    public UUID getId() {
        return id;
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

    public Market getMarket() {
        return market;
    }

    public int getShortfall() {
        return shortfall;
    }

    public BigDecimal getPoints() {
        return points;
    }

    /**
     * Re-states an existing charge rather than adding a second one.
     *
     * <p>Settling is idempotent by skipping periods it has already closed, so
     * this is only reached on a deliberate admin re-run. Updating in place is
     * what keeps that re-run converging on one answer - a delete and re-insert
     * would have to fight the unique key's flush ordering to do the same thing.
     */
    public void restate(int newShortfall, BigDecimal newPoints) {
        this.shortfall = newShortfall;
        this.points = newPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
