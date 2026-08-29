package com.nickspicks.api.admin;

import com.nickspicks.api.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Triggers a Render deploy hook - a single opaque URL that already carries
 * its own key as a query parameter, so there is nothing to authenticate
 * beyond having the URL itself. Kept out of git and out of the frontend
 * bundle by living only in this server's own environment.
 */
@Component
public class RenderDeployClient {

    private final AppProperties.Deploy config;
    private final RestClient restClient;

    public RenderDeployClient(AppProperties properties) {
        this.config = properties.getDeploy();
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return !config.getRenderHookUrl().isBlank();
    }

    public void triggerDeploy() {
        if (!isConfigured()) {
            throw new DeployUnavailableException(
                    "No RENDER_DEPLOY_HOOK_URL configured - cannot trigger a deploy");
        }

        try {
            restClient.post()
                    .uri(config.getRenderHookUrl())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            throw new DeployUnavailableException("Failed to trigger the Render deploy hook", ex);
        }
    }
}
