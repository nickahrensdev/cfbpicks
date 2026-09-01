package com.nickspicks.api.web;

import com.nickspicks.api.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    /**
     * The browser asks permission before it will make this call at all, and
     * actuator answers that question from its own configuration - a CORS
     * mapping registered through WebMvcConfigurer never reaches these
     * endpoints. Getting it wrong returns 403 with no CORS headers, which the
     * browser reports only as a generic "Load failed", so the GET passing
     * proves nothing about whether the page can actually make it.
     */
    @Test
    void allowsThePreflightTheBrowserSendsFirst() throws Exception {
        mockMvc.perform(options("/actuator/info")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    /** An origin the API does not serve is refused rather than allowed by default. */
    @Test
    void refusesAnUnknownOrigin() throws Exception {
        mockMvc.perform(options("/actuator/info")
                        .header("Origin", "https://not-our-site.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** Public, like /actuator/health - no token is sent by the page either. */
    @Test
    void isReadableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
