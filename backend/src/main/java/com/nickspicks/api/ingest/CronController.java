package com.nickspicks.api.ingest;

import com.nickspicks.api.config.AppProperties;
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
 *
 * <p>Only the ESPN score poll is driven this way now. The line refresh used to
 * be, and is an in-process {@code @Scheduled} job instead - see
 * LineRefreshScheduler for why the schedule moved into the app.
 */
@RestController
@RequestMapping("/api/cron")
public class CronController {

    private final AppProperties properties;
    private final EspnScoreIngestService espnScores;

    public CronController(AppProperties properties, EspnScoreIngestService espnScores) {
        this.properties = properties;
        this.espnScores = espnScores;
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
