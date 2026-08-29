package com.nickspicks.api.cfbd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The real quota numbers from CFBD's own {@code /info}, persisted as a
 * single row (id is always 1) so the "refresh at most once a day" rule in
 * {@code CfbdQuotaService} survives this service restarting - an in-memory
 * timestamp would reset to zero every time Render spins the app back up
 * after a period of inactivity, and the throttle would never actually hold.
 */
@Entity
@Table(name = "cfbd_quota_snapshot")
public class CfbdQuotaSnapshot {

    @Id
    private Short id = 1;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "monthly_limit", nullable = false)
    private Integer monthlyLimit;

    @Column(name = "used_calls", nullable = false)
    private Integer usedCalls;

    @Column(name = "remaining_calls", nullable = false)
    private Integer remainingCalls;

    @Column(name = "reset_at")
    private Instant resetAt;

    public Short getId() {
        return id;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Integer getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(Integer monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public Integer getUsedCalls() {
        return usedCalls;
    }

    public void setUsedCalls(Integer usedCalls) {
        this.usedCalls = usedCalls;
    }

    public Integer getRemainingCalls() {
        return remainingCalls;
    }

    public void setRemainingCalls(Integer remainingCalls) {
        this.remainingCalls = remainingCalls;
    }

    public Instant getResetAt() {
        return resetAt;
    }

    public void setResetAt(Instant resetAt) {
        this.resetAt = resetAt;
    }
}
