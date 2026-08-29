package com.nickspicks.api.admin;

import com.nickspicks.api.security.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Redeploys the backend by hitting Render's own deploy hook. */
@RestController
@RequestMapping("/api/admin")
public class DeployController {

    private final CurrentUserService currentUser;
    private final RenderDeployClient render;

    public DeployController(CurrentUserService currentUser, RenderDeployClient render) {
        this.currentUser = currentUser;
        this.render = render;
    }

    @PostMapping("/deploy-backend")
    public Map<String, Object> deployBackend(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        render.triggerDeploy();
        return Map.of("triggered", true);
    }

    /** Whether the deploy hook is configured, so the UI can explain a missing one. */
    @GetMapping("/deploy-backend/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        return Map.of("configured", render.isConfigured());
    }
}
