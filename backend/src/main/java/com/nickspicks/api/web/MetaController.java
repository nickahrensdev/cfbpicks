package com.nickspicks.api.web;

import com.nickspicks.api.config.AppProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handy for confirming which profile actually won at startup, and - via
 * {@code commit} and {@code startedAt} - which build is actually serving.
 *
 * <p>That last part matters more than it sounds. Render swaps instances with
 * no downtime, so a deploy leaves no gap in traffic and nothing externally
 * observable changes unless a migration happens to run. Without a build
 * marker the only way to tell a finished deploy from a still-building one is
 * to find a behaviour difference and infer it, which is slow and easy to get
 * wrong. Deliberately exposes no secrets - the commit sha is already public
 * in the repo.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final AppProperties properties;
    private final Environment environment;

    /** Fixed the moment this JVM built the bean, so it dates the running instance. */
    private final Instant startedAt = Instant.now();

    public MetaController(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @GetMapping
    public Map<String, Object> meta() {
        // getActiveProfiles() is empty when nothing was set explicitly and the
        // app fell back to spring.profiles.default, so report those instead.
        String[] active = environment.getActiveProfiles();
        String[] resolved = active.length > 0 ? active : environment.getDefaultProfiles();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("environment", properties.getEnvironment());
        meta.put("activeProfiles", List.of(resolved));
        meta.put("supabaseConfigured", !properties.getSupabase().getUrl().isBlank());
        // Render sets these on every deploy; absent when running locally.
        meta.put("commit", environment.getProperty("RENDER_GIT_COMMIT", "unknown"));
        meta.put("branch", environment.getProperty("RENDER_GIT_BRANCH", "unknown"));
        meta.put("startedAt", startedAt.toString());
        return meta;
    }
}
