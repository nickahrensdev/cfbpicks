package com.nickspicks.api.cron;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.DataLoadLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line refresh timer.
 *
 * <p>The timer itself always runs - that is the point of moving it in-process -
 * so what matters is that it spends nothing while the job is off. A CFBD call
 * costs real quota, and this is the only thing standing between a switched-off
 * job and a bill.
 */
class LineRefreshSchedulerTest extends IntegrationTest {

    /**
     * Built here rather than injected: the bean carries {@code @Profile("prod")}
     * so it does not exist under the test profile. Constructing it directly
     * tests the class itself, which is the part that matters - the annotation
     * is what keeps a developer's laptop from spending CFBD quota, and is
     * asserted separately below.
     */
    private LineRefreshScheduler scheduler;

    @Autowired
    private CronJobService cronJobs;

    @Autowired
    private com.nickspicks.api.ingest.GameIngestService gameIngest;

    @Autowired
    private com.nickspicks.api.season.CurrentWeekResolver weeks;

    @Autowired
    private com.nickspicks.api.ingest.DataLoadLogService dataLoadLogs;

    @org.junit.jupiter.api.BeforeEach
    void buildScheduler() {
        scheduler = new LineRefreshScheduler(cronJobs, gameIngest, weeks, dataLoadLogs);
    }

    @Autowired
    private CronJobRepository jobs;

    @Autowired
    private DataLoadLogRepository loads;

    @Override
    protected void cleanUp() {
        loads.deleteAll();
        // Seeded by migration, so reset rather than deleted.
        jobs.findById(CronJob.LINES).ifPresent(job -> {
            job.setEnabled(false);
            job.reset();
            jobs.save(job);
        });
    }

    /**
     * The reason the bean is absent outside production. Local and prod share a
     * database, so a container left running would double every tick's CFBD
     * spend against the same rows. Asserted rather than trusted to a comment,
     * because deleting the annotation would otherwise break nothing visible.
     */
    @Test
    void runsInProductionOnly() {
        Profile profile = LineRefreshScheduler.class.getAnnotation(Profile.class);
        assertThat(profile).as("the scheduler must not run outside production").isNotNull();
        assertThat(profile.value()).containsExactly("prod");
    }

    @Test
    void shipsTurnedOff() {
        assertThat(jobs.findById(CronJob.LINES))
                .get()
                .satisfies(job -> assertThat(job.isEnabled())
                        .as("every tick would spend CFBD quota")
                        .isFalse());
    }

    /**
     * The whole safety property: a tick while the job is off must not reach
     * CFBD. A load-log row is written the moment a real refresh begins, so its
     * absence is the evidence that no call was made.
     */
    @Test
    void makesNoCallWhileTurnedOff() {
        scheduler.refreshLines();

        assertThat(loads.findAll())
                .as("no refresh should have started, so no load should be logged")
                .noneMatch(load -> load.getKind() == DataLoadLog.Kind.LINES);
    }

    /** A skipped tick is still recorded - "off" and "not running" differ. */
    @Test
    void recordsThatItSkipped() {
        scheduler.refreshLines();

        assertThat(jobs.findById(CronJob.LINES))
                .get()
                .satisfies(job -> {
                    assertThat(job.getLastRunAt()).isNotNull();
                    assertThat(job.getLastStatus()).isEqualTo(CronJob.Status.SKIPPED);
                    assertThat(job.getLastDetail()).isEqualTo("Turned off");
                });
    }

    // ------------------------------------------------------------- schedule

    /**
     * The board counts down to this, so it has to match the cron expression
     * rather than approximate it - a countdown that ends before the job runs
     * is worse than none.
     */
    @Test
    void alwaysLandsOnTheHourOrTheHalfHour() {
        // Just after the hour, mid-way, exactly on a boundary, and just before.
        assertThat(next("2026-09-03T11:00:01Z")).isEqualTo(Instant.parse("2026-09-03T11:30:00Z"));
        assertThat(next("2026-09-03T11:23:45Z")).isEqualTo(Instant.parse("2026-09-03T11:30:00Z"));
        assertThat(next("2026-09-03T11:30:00Z")).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
        assertThat(next("2026-09-03T11:59:59Z")).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
    }

    /** Rolling past midnight is the case an hour-of-day calculation gets wrong. */
    @Test
    void rollsOverMidnight() {
        assertThat(next("2026-09-03T23:45:00Z")).isEqualTo(Instant.parse("2026-09-04T00:00:00Z"));
    }

    @Test
    void isNeverInThePast() {
        Instant now = Instant.now();
        assertThat(LineRefreshScheduler.nextRun(now)).isAfter(now);
    }

    /** Every answer is on a half-hour boundary, whatever the input. */
    @Test
    void answersOnlyOnBoundaries() {
        Instant probe = Instant.parse("2026-09-03T00:00:00Z");
        for (int i = 0; i < 200; i++) {
            Instant answer = LineRefreshScheduler.nextRun(probe);
            int minute = answer.atZone(ZoneOffset.UTC).getMinute();
            assertThat(minute).isIn(0, 30);
            assertThat(answer.atZone(ZoneOffset.UTC).getSecond()).isZero();
            probe = probe.plusSeconds(437);
        }
    }

    private static Instant next(String at) {
        return LineRefreshScheduler.nextRun(Instant.parse(at));
    }
}
