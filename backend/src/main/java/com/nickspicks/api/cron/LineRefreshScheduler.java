package com.nickspicks.api.cron;

import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.GameIngestService;
import com.nickspicks.api.season.CurrentWeekResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Refreshes every posted betting line, on the half hour.
 *
 * <p>The schedule lives here rather than in Supabase pg_cron. An external
 * caller meant the app could not answer "when is the next refresh" without
 * being told, and left the schedule and the switch in two systems that could
 * disagree about what was running.
 *
 * <p><b>The timer always fires.</b> No {@code @ConditionalOnProperty}. Whether
 * a CFBD call is actually made is decided inside the job, by the {@code
 * cron_job} row, which is what the admin page toggles. A timer that only exists
 * when a flag is set cannot report that it is switched off - which is exactly
 * how the app's original ingest scheduler stayed dead unnoticed until it was
 * deleted.
 *
 * <p>Aligned to :00 and :30 rather than "every 30 minutes from startup", which
 * would drift to whatever minute the app last restarted on. Predictable times
 * are also what let the board count down to the next one without waiting for a
 * first run to happen.
 *
 * <p><b>Production only.</b> Local and production share one Supabase database,
 * so a developer's container left running would fire on the same :00 and :30
 * as the deployed instance, against the same rows - two CFBD calls a tick, and
 * quota spent by a machine nobody thought was working. The bean simply does
 * not exist outside prod.
 *
 * <p>That also means nothing here is exercised by starting the app locally.
 * The tests construct this class directly rather than autowiring it, which is
 * the honest way to test a bean that is deliberately absent.
 *
 * <p>Still assumes a single instance in production - add ShedLock before
 * running more than one Render instance, or every replica will spend a call.
 */
@Component
@Profile("prod")
@EnableScheduling
public class LineRefreshScheduler {

    /** Both halves of the hour, on the minute. Kept in step with {@link #nextRun}. */
    public static final String SCHEDULE = "0 0,30 * * * *";

    /** The zone the schedule is read in. UTC so the two boundaries never move. */
    public static final String ZONE = "UTC";

    private final CronJobRunner runner;
    private final GameIngestService gameIngest;
    private final CurrentWeekResolver weeks;

    public LineRefreshScheduler(CronJobRunner runner, GameIngestService gameIngest,
                                CurrentWeekResolver weeks) {
        this.runner = runner;
        this.gameIngest = gameIngest;
        this.weeks = weeks;
    }

    /**
     * The next :00 or :30 after the given moment.
     *
     * <p>Derived from the schedule rather than from the last run, so the board
     * can count down before the job has ever fired - which it could not do
     * when the schedule was external and the only evidence of it was history.
     */
    public static Instant nextRun(Instant from) {
        Instant onTheMinute = from.truncatedTo(ChronoUnit.MINUTES);
        long minute = onTheMinute.atZone(java.time.ZoneOffset.UTC).getMinute();
        long toAdd = minute < 30 ? 30 - minute : 60 - minute;
        return onTheMinute.plus(Duration.ofMinutes(toAdd));
    }

    /**
     * The switch, the load log and the swallowing of exceptions all live in
     * {@link CronJobRunner} - shared with the stats refresh, so the two cannot
     * drift into recording their runs differently.
     */
    @Scheduled(cron = SCHEDULE, zone = ZONE)
    public void refreshLines() {
        int season = weeks.currentSeason();
        runner.run(CronJob.LINES, season, DataLoadLog.Kind.LINES,
                () -> "%d games updated".formatted(gameIngest.ingestLines(season)));
    }
}
