package com.nickspicks.api.ingest;

import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.config.AppProperties;
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

    public CronController(AppProperties properties, EspnScoreIngestService espnScores,
                          GameIngestService gameIngest, CurrentWeekResolver weeks) {
        this.properties = properties;
        this.espnScores = espnScores;
        this.gameIngest = gameIngest;
        this.weeks = weeks;
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
     * Refreshes every posted betting line for the season - the same one
     * season-wide {@code /lines} call the admin Data page makes, on a
     * schedule instead of a button.
     *
     * <p>Season-wide rather than per-week because that is how CFBD charges
     * for it: one call either way, so narrowing it would only fetch less for
     * the same price.
     *
     * <p>Gated by {@code app.cron.lines-enabled} on top of the shared secret,
     * and that flag ships <em>off</em>. Every call spends real quota against a
     * metered account, so bringing the schedule up and letting it spend money
     * are kept as two separate decisions - a schedule pointed here before the
     * flag is flipped gets a 503 it can be seen failing on, rather than
     * quietly draining the month's allowance.
     *
     * <p>To turn it on: set {@code APP_CRON_LINES_ENABLED=true}, then create
     * the schedule in Supabase (pg_cron), the same way
     * {@code /api/cron/espn-scores} is driven.
     */
    @PostMapping("/lines")
    public ResponseEntity<Object> lines(@RequestHeader(value = "X-Cron-Secret", required = false)
                                        String providedSecret) {
        if (!authorized(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!properties.getCron().isLinesEnabled()) {
            log.info("Line cron called while app.cron.lines-enabled is false - not refreshing");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("enabled", false,
                            "message", "Scheduled line refresh is turned off"));
        }

        int season = weeks.currentSeason();
        try {
            int updated = gameIngest.ingestLines(season);
            return ResponseEntity.ok(Map.of("season", season, "gamesUpdated", updated));
        } catch (CfbdUnavailableException ex) {
            // Upstream being down is not this endpoint failing, and a cron
            // caller retrying on a 5xx would only spend the quota again.
            log.warn("Line cron skipped: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of("season", season, "skipped", ex.getMessage()));
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
