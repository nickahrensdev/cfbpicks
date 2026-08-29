package com.nickspicks.api.web;

import com.nickspicks.api.config.AppProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Handy for confirming which profile actually won at startup.
 * Deliberately exposes no secrets.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final AppProperties properties;
    private final Environment environment;

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

        return Map.of(
                "environment", properties.getEnvironment(),
                "activeProfiles", List.of(resolved),
                "supabaseConfigured", !properties.getSupabase().getUrl().isBlank());
    }
}
