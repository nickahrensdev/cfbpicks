package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.cron.CronJob;
import com.nickspicks.api.cron.CronJobService;
import com.nickspicks.api.season.CurrentWeekResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Endpoints meant to be called by an external scheduler (Supabase pg_cron)
 * rather than a signed-in member - see {@code SecurityConfig}, which lets
 * {@code /api/cron/**} through Spring Security entirely so a shared secret
 * can be checked here instead of a Supabase JWT, which this caller has none
 * of.
 */
@RestController
@RequestMapping("/api/cron")
public class CronController {

    private static final Logger log = LoggerFactory.getLogger(CronController.class);

    private final AppProperties properties;
    private final EspnScoreIngestService espnScores;
    private final GameIngestService gameIngest;
    private final CurrentWeekResolver weeks;
    private final CronJobService cronJobs;
    private final DataLoadLogService dataLoadLogs;

    public CronController(AppProperties properties, EspnScoreIngestService espnScores,
                          GameIngestService gameIngest, CurrentWeekResolver weeks,
                          CronJobService cronJobs, DataLoadLogService dataLoadLogs) {
        this.properties = properties;
        this.espnScores = espnScores;
        this.gameIngest = gameIngest;
        this.weeks = weeks;
        this.cronJobs = cronJobs;
        this.dataLoadLogs = dataLoadLogs;
    }

    @PostMapping("/espn-scores")
    public ResponseEntity<Object> espnScores(@RequestHeader(value = "X-Cron-Secret", required = false)
                                             String providedSecret) {
        if (!authorized(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        EspnScoreIngestService.Result result = espnScores.pollAndGrade();
        return ResponseEntity.ok(Map.of(
                "gamesUpdated", result.gamesUpdated(),
                "gamesGraded", result.gamesGraded()));
    }

    /**
     * Refreshes every posted betting line for the season.
     *
     * <p>Season-wide because that is how CFBD charges for it: one call either
     * way, so narrowing it would fetch less for the same price.
     *
     * <p>Whether it acts is a row in {@code cron_job}, not a config property,
     * so it can be stopped from the admin page rather than by a redeploy. The
     * schedule keeps calling either way and this declines - see V26 for why
     * the app does not reach into pg_cron itself.
     *
     * <p>Every outcome is recorded twice over: on the job row, which is what
     * the admin page and the board's countdown read, and as a row in the load
     * log beside the manual buttons, so a cron run is visible in the same
     * place as everything else that touches the data.
     */
    @PostMapping("/lines")
    public ResponseEntity<Object> lines(@RequestHeader(value = "X-Cron-Secret", required = false)
                                        String providedSecret) {
        if (!authorized(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!cronJobs.isEnabled(CronJob.LINES)) {
            // Recorded, so a stopped job still shows when it was last called -
            // the difference between "turned off" and "not being called at
            // all" is exactly what an admin needs to see when it looks broken.
            cronJobs.record(CronJob.LINES, CronJob.Status.SKIPPED, "Turned off");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("enabled", false,
                            "message", "Scheduled line refresh is turned off"));
        }

        int season = weeks.currentSeason();
        DataLoadLog started = dataLoadLogs.startForCron(DataLoadLog.Kind.LINES, season);

        try {
            int updated = gameIngest.ingestLines(season);
            String detail = "%d games updated".formatted(updated);
            dataLoadLogs.succeed(started.getId(), detail);
            cronJobs.record(CronJob.LINES, CronJob.Status.SUCCESS, detail);
            return ResponseEntity.ok(Map.of("season", season, "gamesUpdated", updated));
        } catch (CfbdUnavailableException ex) {
            // Upstream being down is not this endpoint failing, and a cron
            // caller retrying on a 5xx would only spend the quota again.
            log.warn("Line cron skipped: {}", ex.getMessage());
            dataLoadLogs.fail(started.getId(), ex.getMessage());
            cronJobs.record(CronJob.LINES, CronJob.Status.FAILED, ex.getMessage());
            return ResponseEntity.ok(Map.of("season", season, "skipped", ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Line cron failed", ex);
            dataLoadLogs.fail(started.getId(), ex.getMessage());
            cronJobs.record(CronJob.LINES, CronJob.Status.FAILED, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Constant-time comparison - a naive {@code equals} leaks how many
     * leading characters matched through response timing, which matters for
     * a value this endpoint's entire security rests on. A blank configured
     * secret always rejects, so an unset {@code CRON_SECRET} can never be
     * satisfied by an empty or missing header.
     */
    private boolean authorized(String providedSecret) {
        String configured = properties.getCron().getSecret();
        if (configured.isBlank() || providedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
