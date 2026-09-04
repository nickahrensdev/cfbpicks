package com.nickspicks.api.cron;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.DataLoadLogRepository;
import com.nickspicks.api.ingest.ReferenceIngestService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.team.TeamAtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rankings / records / ATS timer.
 *
 * <p>Three CFBD calls a tick, so the property worth protecting is the same one
 * as the line refresh: a tick while the job is switched off must reach CFBD
 * zero times. Everything else here is guarding the reasons the job exists at
 * all - it ships off, and it does not run outside production.
 */
class StatsRefreshSchedulerTest extends IntegrationTest {

    /** Constructed rather than injected - {@code @Profile("prod")}, as below. */
    private StatsRefreshScheduler scheduler;

    @Autowired
    private CronJobRunner runner;

    @Autowired
    private ReferenceIngestService referenceIngest;

    @Autowired
    private TeamAtsService teamAts;

    @Autowired
    private CurrentWeekResolver weeks;

    @Autowired
    private CronJobRepository jobs;

    @Autowired
    private DataLoadLogRepository loads;

    @BeforeEach
    void buildScheduler() {
        scheduler = new StatsRefreshScheduler(runner, referenceIngest, teamAts, weeks);
    }

    @Override
    protected void cleanUp() {
        loads.deleteAll();
        // Seeded by migration, so reset rather than deleted.
        jobs.findById(CronJob.STATS).ifPresent(job -> {
            job.setEnabled(false);
            job.reset();
            jobs.save(job);
        });
    }

    /**
     * Local and production share a database, so a developer's container left
     * running would spend three calls a day against the same rows. Asserted
     * rather than trusted to a comment - deleting the annotation would
     * otherwise break nothing visible.
     */
    @Test
    void runsInProductionOnly() {
        Profile profile = StatsRefreshScheduler.class.getAnnotation(Profile.class);
        assertThat(profile).as("the scheduler must not run outside production").isNotNull();
        assertThat(profile.value()).containsExactly("prod");
    }

    @Test
    void shipsTurnedOff() {
        assertThat(jobs.findById(CronJob.STATS))
                .as("the migration must seed the row, or the job can never be switched on")
                .get()
                .satisfies(job -> assertThat(job.isEnabled())
                        .as("every tick would spend three CFBD calls")
                        .isFalse());
    }

    /**
     * The safety property. A load-log row is written the moment each of the
     * three loads begins, so the absence of all three is the evidence that no
     * call was made.
     */
    @Test
    void makesNoCallsWhileTurnedOff() {
        scheduler.refreshStats();

        assertThat(loads.findAll())
                .as("no load should have started, so none should be logged")
                .noneMatch(load -> List.of(DataLoadLog.Kind.RANKINGS,
                                DataLoadLog.Kind.REFERENCE,
                                DataLoadLog.Kind.ATS)
                        .contains(load.getKind()));
    }

    /** A skipped tick is still recorded - "off" and "not running" differ. */
    @Test
    void recordsThatItSkipped() {
        scheduler.refreshStats();

        assertThat(jobs.findById(CronJob.STATS))
                .get()
                .satisfies(job -> {
                    assertThat(job.getLastRunAt()).isNotNull();
                    assertThat(job.getLastStatus()).isEqualTo(CronJob.Status.SKIPPED);
                    assertThat(job.getLastDetail()).isEqualTo("Turned off");
                });
    }

    /**
     * The interval is what the admin page counts forward from to say when the
     * next run is due, so a row claiming half an hour for a daily job would
     * show a "next run" eleven and a half hours early, every day.
     */
    @Test
    void isSeededAsDaily() {
        assertThat(jobs.findById(CronJob.STATS))
                .get()
                .satisfies(job -> assertThat(job.getIntervalSeconds()).isEqualTo(86_400));
    }
}
