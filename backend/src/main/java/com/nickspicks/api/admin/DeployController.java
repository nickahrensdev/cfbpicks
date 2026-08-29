package com.nickspicks.api.admin;

import com.nickspicks.api.security.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Redeploys the backend, by way of a GitHub Actions workflow rather than
 * calling Render directly. Keeps the Render deploy hook's key out of every
 * layer this server touches - see {@link GithubActionsClient}.
 */
@RestController
@RequestMapping("/api/admin")
public class DeployController {

    private final CurrentUserService currentUser;
    private final GithubActionsClient github;

    public DeployController(CurrentUserService currentUser, GithubActionsClient github) {
        this.currentUser = currentUser;
        this.github = github;
    }

    @PostMapping("/deploy-backend")
    public Map<String, Object> deployBackend(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        github.dispatchDeploy();
        return Map.of("triggered", true);
    }

    /** Whether a dispatch token is configured, so the UI can explain a missing one. */
    @GetMapping("/deploy-backend/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);
        return Map.of("configured", github.isConfigured());
    }
}
