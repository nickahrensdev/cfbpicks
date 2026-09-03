package com.nickspicks.api.cron;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * When the board's numbers were last refreshed, and when they will be next.
 *
 * <p>Member-facing and deliberately separate from the admin endpoint: this
 * says only when, never whether anyone may change it, so it needs no admin
 * check and leaks nothing. It is the one piece of the cron machinery an
 * ordinary member has a reason to see - a spread that is about to move is
 * worth waiting thirty seconds for.
 *
 * <p>Not under {@code /api/cron/**}, which SecurityConfig lets through
 * unauthenticated for the shared-secret callers. This is an ordinary
 * authenticated endpoint.
 */
@RestController
@RequestMapping("/api/line-refresh")
public class LineRefreshController {

    private final CronJobService cronJobs;

    public LineRefreshController(CronJobService cronJobs) {
        this.cronJobs = cronJobs;
    }

    /**
     * @param enabled  whether refreshes are running at all. When false the
     *                 board says the lines are static rather than counting
     *                 down to something that will not happen.
     * @param nextRunAt the next :00 or :30, computed from the schedule rather
     *                  than from the last run - so the board counts down
     *                  correctly before the job has ever fired, which it could
     *                  not do while the schedule lived outside the app.
     */
    public record LineRefreshStatus(boolean enabled, int intervalSeconds,
                                    Instant lastRunAt, Instant nextRunAt) {
    }

    @GetMapping
    public LineRefreshStatus status() {
        CronJob job = cronJobs.require(CronJob.LINES);
        return new LineRefreshStatus(
                job.isEnabled(),
                job.getIntervalSeconds(),
                job.getLastRunAt(),
                LineRefreshScheduler.nextRun(java.time.Instant.now()));
    }
}
