package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdClient;
import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled ingest, budgeted against the CFBD free tier of 1,000 calls/month.
 *
 * <pre>
 *   schedule sync   1/week          ~5/month
 *   line sync       every 3h        ~150/month
 *   score poll      every 15m, but only while a game is actually live
 * </pre>
 *
 * <p>The score poll checks our own tables before calling out, so days with no
 * football cost nothing. Budget lands near 350/month.
 *
 * <p>Assumes a single instance. Add ShedLock before scaling horizontally or
 * every job will run once per replica.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.cfbd", name = "enabled", havingValue = "true")
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final GameIngestService gameIngest;
    private final ReferenceIngestService referenceIngest;
    private final CfbdClient cfbd;
    private final AppProperties properties;

    public IngestScheduler(GameIngestService gameIngest, ReferenceIngestService referenceIngest,
                           CfbdClient cfbd, AppProperties properties) {
        this.gameIngest = gameIngest;
        this.referenceIngest = referenceIngest;
        this.cfbd = cfbd;
        this.properties = properties;
    }

    /**
     * Sunday 03:00 - the season schedule and the week's new polls, plus teams
     * if this is a new season.
     */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "America/Chicago")
    public void syncSchedule() {
        int season = properties.getPickem().getSeason();
        run("schedule sync", () -> {
            if (!referenceIngest.teamsSynced(season)) {
                referenceIngest.ingestCalendar(season);
                referenceIngest.ingestTeams(season);
                referenceIngest.ingestCoaches(season);
            }
            gameIngest.ingestSchedule(season);
            // Polls publish Sunday afternoon for the committee and Tuesday for
            // AP; re-running weekly keeps all of them current.
            referenceIngest.ingestRankings(season);
        });
    }

    /** Every three hours - line movement. */
    @Scheduled(cron = "0 0 */3 * * *", zone = "America/Chicago")
    public void syncLines() {
        int season = properties.getPickem().getSeason();
        run("line sync", () -> gameIngest.ingestLines(season));
    }

    /** Every 15 minutes, but only calls out when a game could be underway. */
    @Scheduled(fixedDelayString = "PT15M")
    public void syncScores() {
        if (!gameIngest.hasLiveGames()) {
            return;
        }
        int season = properties.getPickem().getSeason();
        run("score sync", () -> gameIngest.ingestScores(season));
    }

    /** Daily quota reminder, so an overrun is visible before it bites. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Chicago")
    public void reportQuota() {
        log.info("CFBD calls in the trailing 30 days: {}", cfbd.callsThisMonth());
    }

    private void run(String label, Runnable job) {
        try {
            job.run();
        } catch (CfbdUnavailableException ex) {
            // Upstream problems must not kill the scheduler thread.
            log.warn("{} skipped: {}", label, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("{} failed", label, ex);
        }
    }
}
