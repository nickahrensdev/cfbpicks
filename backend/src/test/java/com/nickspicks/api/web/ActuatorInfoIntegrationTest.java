package com.nickspicks.api.web;

import com.nickspicks.api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the admin Data page reads to say which build is running.
 *
 * <p>Worth a test because the interesting failure is silent: the endpoint
 * answers 200 with {@code {}} when a build was packaged without the maven
 * plugin's build-info goal, which looks exactly like a healthy response and
 * leaves the panel reporting nothing.
 */
class ActuatorInfoIntegrationTest extends IntegrationTest {

    @Override
    protected void cleanUp() {
        // Nothing written - this only reads what the app knows about itself.
    }

    @Test
    void reportsTheRunningBuild() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("nickspicks-api"))
                .andExpect(jsonPath("$.build.version").isNotEmpty())
                .andExpect(jsonPath("$.build.time").isNotEmpty());
    }

    /** The profile is the fact the panel exists to answer. */
    @Test
    void reportsWhichConfigurationItCameUpOn() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(jsonPath("$.app.profile").isNotEmpty())
                .andExpect(jsonPath("$.java.version").isNotEmpty())
                .andExpect(jsonPath("$.os.name").isNotEmpty());
    }

    /** Public, like /actuator/health - no token is sent by the page either. */
    @Test
    void isReadableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
