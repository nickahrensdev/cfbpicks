package com.nickspicks.api.cron;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One scheduled job, and whether it should act when its schedule calls.
 *
 * <p>The schedule itself lives in the scheduler class, as a cron expression.
 * This row is the switch and the record of what happened last, which is what
 * makes both the admin toggle and the picks board's countdown possible without
 * either one hardcoding the interval.
 */
@Entity
@Table(name = "cron_job")
public class CronJob {

    /** Matches the last segment of the endpoint's path. */
    public static final String LINES = "lines";

    /** Rankings, team records and ATS - see {@code StatsRefreshScheduler}. */
    public static final String STATS = "stats";

    /** The season's game schedule - see {@code ScheduleRefreshScheduler}. */
    public static final String SCHEDULE = "schedule";

    public enum Status {
        SUCCESS, FAILED, SKIPPED
    }

    @Id
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status")
    private Status lastStatus;

    @Column(name = "last_detail")
    private String lastDetail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CronJob() {
    }

    /**
     * When the schedule is next expected to call, or null before it ever has.
     *
     * <p>Derived from the last run rather than stored, so it cannot go stale.
     * It is an estimate: pg_cron fires on wall-clock boundaries while this
     * counts forward from when the last run happened, so the two drift by
     * however long a run takes. Seconds, against a thirty-minute interval.
     */
    public Instant nextRunAt() {
        return lastRunAt == null ? null : lastRunAt.plusSeconds(intervalSeconds);
    }

    public void record(Status status, String detail) {
        this.lastStatus = status;
        // The column is 500; a stack-trace-ish message from upstream should
        // not be what fails the write that is recording the failure.
        this.lastDetail = detail == null || detail.length() <= 500
                ? detail
                : detail.substring(0, 500);
        this.lastRunAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Back to never-run. For tests, which share one database per suite. */
    public void reset() {
        this.lastRunAt = null;
        this.lastStatus = null;
        this.lastDetail = null;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Status getLastStatus() {
        return lastStatus;
    }

    public String getLastDetail() {
        return lastDetail;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
