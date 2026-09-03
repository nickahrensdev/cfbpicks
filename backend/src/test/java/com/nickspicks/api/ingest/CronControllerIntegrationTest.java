package com.nickspicks.api.ingest;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.GameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole security model of {@code /api/cron/**} rests on this comparison
 * - it carries no Supabase JWT at all (see {@code SecurityConfig}), so a
 * broken or missing secret check would leave score updates and grading
 * open to anyone who finds the URL.
 */
@TestPropertySource(properties = "app.cron.secret=letmein")
class CronControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private GameRepository games;

    @Override
    protected void cleanUp() {
        games.deleteAll();
    }

    @Test
    void rejectsAMissingSecret() throws Exception {
        mockMvc.perform(post("/api/cron/espn-scores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTheWrongSecret() throws Exception {
        mockMvc.perform(post("/api/cron/espn-scores").header("X-Cron-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsTheConfiguredSecretWithNoJwtAtAll() throws Exception {
        // No .with(jwt()...) anywhere here - proving this path needs none.
        mockMvc.perform(post("/api/cron/espn-scores").header("X-Cron-Secret", "letmein"))
                .andExpect(status().isOk());
    }

}
