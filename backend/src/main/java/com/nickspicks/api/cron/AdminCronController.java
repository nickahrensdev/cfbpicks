package com.nickspicks.api.cron;

import com.nickspicks.api.security.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Starting and stopping the scheduled jobs.
 *
 * <p>Admin-gated first line, like every other endpoint under /api/admin -
 * there is no URL-level rule for that prefix, so each handler enforces it.
 */
@RestController
@RequestMapping("/api/admin/cron")
public class AdminCronController {

    private final CronJobService cronJobs;
    private final CurrentUserService currentUser;

    public AdminCronController(CronJobService cronJobs, CurrentUserService currentUser) {
        this.cronJobs = cronJobs;
        this.currentUser = currentUser;
    }

    public record CronJobRow(String name, boolean enabled, int intervalSeconds,
                             java.time.Instant lastRunAt, String lastStatus, String lastDetail,
                             java.time.Instant nextRunAt) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    @GetMapping
    public List<CronJobRow> list(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        return cronJobs.all().stream().map(AdminCronController::row).toList();
    }

    /** Every job at once - the "stop everything" switch. */
    @PutMapping
    public List<CronJobRow> setAll(@AuthenticationPrincipal Jwt jwt,
                                   @RequestBody EnabledRequest request) {
        currentUser.requireAdmin(jwt);
        return cronJobs.setAllEnabled(request.enabled()).stream()
                .map(AdminCronController::row)
                .toList();
    }

    @PutMapping("/{name}")
    public CronJobRow set(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable String name,
                          @RequestBody EnabledRequest request) {
        currentUser.requireAdmin(jwt);
        return row(cronJobs.setEnabled(name, request.enabled()));
    }

    private static CronJobRow row(CronJob job) {
        return new CronJobRow(
                job.getName(),
                job.isEnabled(),
                job.getIntervalSeconds(),
                job.getLastRunAt(),
                job.getLastStatus() == null ? null : job.getLastStatus().name(),
                job.getLastDetail(),
                job.nextRunAt());
    }
}
