package com.nickspicks.api.cron;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.DataLoadLogRepository;
import com.nickspicks.api.ingest.GameIngestService;
import com.nickspicks.api.ingest.ReferenceIngestService;
import com.nickspicks.api.season.CurrentWeekResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The daily schedule refresh.
 *
 * <p>Same safety property as the other two jobs - a tick while switched off
 * must reach CFBD zero times - plus the reason this job is daily at all: it
 * asks for the whole season, so a kickoff that moves weeks ahead is picked up
 * without spending a call per week.
 */
class ScheduleRefreshSchedulerTest extends IntegrationTest {

    /** Constructed rather than injected - {@code @Profile("prod")}, as below. */
    private ScheduleRefreshScheduler scheduler;

    @Autowired
    private CronJobRunner runner;

    @Autowired
    private GameIngestService gameIngest;

    @Autowired
    private ReferenceIngestService referenceIngest;

    @Autowired
    private CurrentWeekResolver weeks;

    @Autowired
    private CronJobRepository jobs;

    @Autowired
    private DataLoadLogRepository loads;

    @BeforeEach
    void buildScheduler() {
        scheduler = new ScheduleRefreshScheduler(runner, gameIngest, referenceIngest, weeks);
    }

    @Override
    protected void cleanUp() {
        loads.deleteAll();
        // Seeded by migration, so reset rather than deleted.
        jobs.findById(CronJob.SCHEDULE).ifPresent(job -> {
            job.setEnabled(false);
            job.reset();
            jobs.save(job);
        });
    }

    /**
     * Local and production share a database, so a developer's container left
     * running would spend a call a day against the same rows.
     */
    @Test
    void runsInProductionOnly() {
        Profile profile = ScheduleRefreshScheduler.class.getAnnotation(Profile.class);
        assertThat(profile).as("the scheduler must not run outside production").isNotNull();
        assertThat(profile.value()).containsExactly("prod");
    }

    @Test
    void shipsTurnedOff() {
        assertThat(jobs.findById(CronJob.SCHEDULE))
                .as("the migration must seed the row, or the job can never be switched on")
                .get()
                .satisfies(job -> assertThat(job.isEnabled()).isFalse());
    }

    /** The safety property: a tick while off writes no load, so makes no call. */
    @Test
    void makesNoCallsWhileTurnedOff() {
        scheduler.refreshSchedule();

        assertThat(loads.findAll())
                .as("no load should have started, so none should be logged")
                .noneMatch(load -> List.of(DataLoadLog.Kind.GAMES, DataLoadLog.Kind.REFERENCE)
                        .contains(load.getKind()));
    }

    /** A skipped tick is still recorded - "off" and "not running" differ. */
    @Test
    void recordsThatItSkipped() {
        scheduler.refreshSchedule();

        assertThat(jobs.findById(CronJob.SCHEDULE))
                .get()
                .satisfies(job -> {
                    assertThat(job.getLastRunAt()).isNotNull();
                    assertThat(job.getLastStatus()).isEqualTo(CronJob.Status.SKIPPED);
                    assertThat(job.getLastDetail()).isEqualTo("Turned off");
                });
    }

    /**
     * The admin page counts forward from this to say when the next run is due,
     * so a row claiming half an hour for a daily job would show a "next run"
     * eleven and a half hours early, every day.
     */
    @Test
    void isSeededAsDaily() {
        assertThat(jobs.findById(CronJob.SCHEDULE))
                .get()
                .satisfies(job -> assertThat(job.getIntervalSeconds()).isEqualTo(86_400));
    }
}
