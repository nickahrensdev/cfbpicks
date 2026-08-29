package com.nickspicks.api.admin;

import com.nickspicks.api.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Fires a {@code workflow_dispatch} event on the repo's deploy-backend
 * workflow, which is the thing that actually calls Render's deploy hook.
 *
 * <p>The Render hook's key never reaches this server - it lives only as an
 * encrypted GitHub Actions secret, decrypted for the few seconds the
 * workflow runs. This class only needs a token scoped to triggering
 * workflows on this one repo, which is a strictly smaller thing to leak than
 * a key that can redeploy production on its own.
 */
@Component
public class GithubActionsClient {

    private static final String BASE_URL = "https://api.github.com";

    private final AppProperties.Deploy config;
    private final RestClient restClient;

    public GithubActionsClient(AppProperties properties) {
        this.config = properties.getDeploy();
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean isConfigured() {
        return !config.getGithubToken().isBlank();
    }

    /** Triggers the workflow on {@code main}. Throws if no token is set. */
    public void dispatchDeploy() {
        if (!isConfigured()) {
            throw new DeployUnavailableException(
                    "No GITHUB_DISPATCH_TOKEN configured - cannot trigger a deploy");
        }

        try {
            restClient.post()
                    .uri("/repos/{repo}/actions/workflows/{workflow}/dispatches",
                            config.getGithubRepo(), config.getWorkflowFile())
                    .header("Authorization", "Bearer " + config.getGithubToken())
                    .body(Map.of("ref", "main"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            throw new DeployUnavailableException("Failed to trigger the deploy workflow", ex);
        }
    }
}
