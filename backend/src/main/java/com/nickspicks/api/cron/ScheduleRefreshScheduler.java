package com.nickspicks.api.cron;

import com.nickspicks.api.ingest.DataLoadLog;
import com.nickspicks.api.ingest.GameIngestService;
import com.nickspicks.api.ingest.ReferenceIngestService;
import com.nickspicks.api.season.CurrentWeekResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes the whole season's game schedule, daily.
 *
 * <p>The whole season, not the current week. Kickoff times move for television
 * well ahead of the game, and a pick's lock window is computed from kickoff, so
 * a stale time two weeks out is a wrong lock window. One CFBD call covers every
 * week anyway - asking week by week costs a call each for the same rows - so
 * refreshing everything is both the correct answer and the cheap one.
 *
 * <p><b>No scores, no status.</b> Those belong to the ESPN poller, which runs
 * every minute and costs nothing. CFBD's /games lags behind it, and letting a
 * daily job write scores meant a finished game could be reverted to live with
 * its score nulled, with nothing to repair it. See {@code
 * GameIngestService#ingestSchedule}.
 *
 * <p><b>Production only</b>, like the other two: local and production share one
 * Supabase database, so a developer's container would spend quota against the
 * same rows.
 */
@Component
@Profile("prod")
@EnableScheduling
public class ScheduleRefreshScheduler {

    /**
     * An hour before the stats refresh. Nothing depends on the order - they
     * touch different tables - but two jobs on one instance at the same instant
     * share a connection pool for no reason.
     */
    public static final String SCHEDULE = "0 0 8 * * *";

    public static final String ZONE = "UTC";

    private final CronJobRunner runner;
    private final GameIngestService gameIngest;
    private final ReferenceIngestService referenceIngest;
    private final CurrentWeekResolver weeks;

    public ScheduleRefreshScheduler(CronJobRunner runner, GameIngestService gameIngest,
                                    ReferenceIngestService referenceIngest,
                                    CurrentWeekResolver weeks) {
        this.runner = runner;
        this.gameIngest = gameIngest;
        this.referenceIngest = referenceIngest;
        this.weeks = weeks;
    }

    @Scheduled(cron = SCHEDULE, zone = ZONE)
    public void refreshSchedule() {
        int season = weeks.currentSeason();

        List<CronJobRunner.Step> steps = new ArrayList<>();

        /*
         * The calendar, teams and coaches, but only the first time this season
         * is seen. Three calls once a year rather than three a day: none of it
         * changes in-season, and teamsSynced is a row in our own database, so
         * asking costs nothing.
         *
         * Here rather than left manual because the season rolls over on its
         * own now - CurrentWeekResolver follows ESPN into the new year without
         * a deploy - and a schedule refresh for a season whose teams were never
         * loaded would leave every game pointing at teams that do not exist.
         */
        if (!referenceIngest.teamsSynced(season)) {
            steps.add(new CronJobRunner.Step(DataLoadLog.Kind.REFERENCE, () -> {
                int weeksLoaded = referenceIngest.ingestCalendar(season);
                int teams = referenceIngest.ingestTeams(season);
                int coaches = referenceIngest.ingestCoaches(season);
                return "%d weeks, %d teams, %d coaches".formatted(weeksLoaded, teams, coaches);
            }));
        }

        steps.add(new CronJobRunner.Step(DataLoadLog.Kind.GAMES,
                () -> "%d games".formatted(gameIngest.ingestSchedule(season))));

        runner.run(CronJob.SCHEDULE, season, steps);
    }
}
