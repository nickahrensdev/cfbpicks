package com.nickspicks.api.cron;

import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.ReferenceIngestService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.team.TeamAtsService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refreshes the poll rankings, every team's record and every team's ATS line.
 *
 * <p>These are what a team page is made of, and until this job none of them had
 * a working schedule. Rankings had a weekly job in an {@code IngestScheduler}
 * class, but that was gated on a property production set to false, so it never
 * ran; it has since been deleted. Records and ATS had no scheduled refresh at
 * all, in any class. All three moved only when an admin pressed the buttons on
 * the Data page.
 *
 * <p>Daily rather than weekly. Only the records really change that often, but
 * three calls a day is 90 a month against a 5,000 limit, and there is no one
 * right day to pick: the AP and Coaches polls publish Sunday afternoon, the CFP
 * committee's rankings Tuesday night, and late-season games run into midweek.
 * Running every day costs less than being wrong about which day.
 *
 * <p>Switched by a database row rather than a property, which is the lesson of
 * the class this replaced: a job that only exists when a flag is set cannot
 * report on the admin page that it is switched off, so it sat dead for a season
 * with nothing on any screen to say so.
 *
 * <p><b>Production only</b>, for the same reason as {@link
 * LineRefreshScheduler}: local and production share one Supabase database, so a
 * developer's container would spend quota against the same rows.
 */
@Component
@Profile("prod")
@EnableScheduling
public class StatsRefreshScheduler {

    /** Early morning UTC - after any poll release and any midweek game. */
    public static final String SCHEDULE = "0 0 9 * * *";

    public static final String ZONE = "UTC";

    private final CronJobRunner runner;
    private final ReferenceIngestService referenceIngest;
    private final TeamAtsService teamAts;
    private final CurrentWeekResolver weeks;

    public StatsRefreshScheduler(CronJobRunner runner, ReferenceIngestService referenceIngest,
                                 TeamAtsService teamAts, CurrentWeekResolver weeks) {
        this.runner = runner;
        this.referenceIngest = referenceIngest;
        this.teamAts = teamAts;
        this.weeks = weeks;
    }

    @Scheduled(cron = SCHEDULE, zone = ZONE)
    public void refreshStats() {
        int season = weeks.currentSeason();

        // Ordered as they are read: the poll first, then the record beside it,
        // then the ATS line under it. Nothing depends on the order - each step
        // stands alone and a failure in one does not stop the next.
        runner.run(CronJob.STATS, season, List.of(
                new CronJobRunner.Step(DataLoadLog.Kind.RANKINGS,
                        () -> "%d ranking rows".formatted(referenceIngest.ingestRankings(season))),
                // Records are part of the REFERENCE load's "records" part, and
                // share its kind so the two appear as one thing in the history.
                new CronJobRunner.Step(DataLoadLog.Kind.REFERENCE,
                        () -> "%d team records".formatted(referenceIngest.ingestRecords(season))),
                new CronJobRunner.Step(DataLoadLog.Kind.ATS,
                        () -> "%d team ATS rows".formatted(teamAts.refreshSeason(season)))));
    }
}
