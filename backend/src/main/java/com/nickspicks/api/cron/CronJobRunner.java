package com.nickspicks.api.cron;

import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.DataLoadLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The part every scheduled job does the same way: ask whether it is switched
 * on, do the work, and put the outcome somewhere an admin can read it.
 *
 * <p>Extracted when the second job arrived. The bookkeeping is easy to get
 * subtly wrong - a skipped run that records nothing looks identical to a
 * schedule that has stopped firing, and an exception escaping a {@code
 * @Scheduled} method kills the run silently - so having one copy of it matters
 * more than the few lines it saves.
 */
@Component
public class CronJobRunner {

    private static final Logger log = LoggerFactory.getLogger(CronJobRunner.class);

    /**
     * One load inside a job.
     *
     * <p>A job may do several - the stats refresh pulls rankings, records and
     * ATS - and each gets its own {@code data_load_log} row under its own kind,
     * so a scheduled run appears on the admin Data page as the same rows the
     * equivalent manual buttons would have written. One row covering three
     * loads would have needed a kind that no button produces, and would have
     * hidden which of the three was the one that failed.
     *
     * @param work returns a one-line description of what it did
     */
    public record Step(DataLoadLog.Kind kind, Supplier<String> work) {
    }

    private final CronJobService jobs;
    private final DataLoadLogService dataLoadLogs;

    public CronJobRunner(CronJobService jobs, DataLoadLogService dataLoadLogs) {
        this.jobs = jobs;
        this.dataLoadLogs = dataLoadLogs;
    }

    /** A job that does exactly one load. */
    public void run(String jobName, int season, DataLoadLog.Kind kind, Supplier<String> work) {
        run(jobName, season, List.of(new Step(kind, work)));
    }

    /**
     * Runs the steps if the job is enabled, and records what happened either
     * way.
     *
     * <p>A failing step does not stop the ones after it. The three stats loads
     * are independent - poll results being unavailable is no reason to leave
     * ATS a week stale - and a job that abandoned the rest on the first problem
     * would quietly under-refresh for as long as the first step stayed broken.
     */
    public void run(String jobName, int season, List<Step> steps) {
        // Asked every time, so switching a job off takes effect on the next
        // tick with no restart.
        if (!jobs.isEnabled(jobName)) {
            // Recorded, because "turned off" and "the schedule is not firing at
            // all" look identical otherwise and need different fixes.
            jobs.record(jobName, CronJob.Status.SKIPPED, "Turned off");
            log.debug("{} skipped - turned off", jobName);
            return;
        }

        List<String> details = new ArrayList<>();
        boolean failed = false;

        for (Step step : steps) {
            DataLoadLog started = dataLoadLogs.startForCron(step.kind(), season);
            try {
                String detail = step.work().get();
                dataLoadLogs.succeed(started.getId(), detail);
                details.add(detail);
                log.info("{}/{}: {}", jobName, step.kind(), detail);
            } catch (CfbdUnavailableException ex) {
                // Upstream being down is not this job failing. Recorded and
                // left; the next tick will try again.
                dataLoadLogs.fail(started.getId(), ex.getMessage());
                details.add("%s unavailable".formatted(step.kind()));
                failed = true;
                log.warn("{}/{} skipped: {}", jobName, step.kind(), ex.getMessage());
            } catch (RuntimeException ex) {
                // Swallowed deliberately: an exception out of a @Scheduled
                // method stops nothing else, but it also tells nobody. The job
                // row and the load log are where this is meant to be read.
                dataLoadLogs.fail(started.getId(), ex.getMessage());
                details.add("%s failed".formatted(step.kind()));
                failed = true;
                log.error("{}/{} failed", jobName, step.kind(), ex);
            }
        }

        // The summary beside the job on the admin page. Every step appears,
        // successes included, so a run that half worked reads as one.
        jobs.record(jobName,
                failed ? CronJob.Status.FAILED : CronJob.Status.SUCCESS,
                String.join(", ", details));
    }
}
