package com.nickspicks.api.ingest;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.cron.CronJob;
import com.nickspicks.api.cron.CronJobRepository;
import com.nickspicks.api.game.GameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private CronJobRepository cronJobs;

    @Override
    protected void cleanUp() {
        games.deleteAll();
        // The job row is seeded by migration, not by a test, so it is reset
        // rather than deleted - a missing row would fail every later test.
        cronJobs.findById(CronJob.LINES).ifPresent(job -> {
            job.setEnabled(false);
            job.reset();
            cronJobs.save(job);
        });
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

    @Test
    void theLineRefreshChecksTheSecretToo() throws Exception {
        mockMvc.perform(post("/api/cron/lines"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cron/lines").header("X-Cron-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The point of the switch: a schedule pointed at this endpoint before
     * anyone has decided to spend CFBD quota on it must not spend any. If this
     * ever returns 200 while the job is off, it is billing the account.
     *
     * <p>The job ships off - see V26 - so this asserts the shipped state.
     */
    @Test
    void theLineRefreshDoesNothingWhileItIsTurnedOff() throws Exception {
        assertThat(cronJobs.findById(CronJob.LINES))
                .get()
                .satisfies(job -> assertThat(job.isEnabled())
                        .as("the lines job must ship turned off")
                        .isFalse());

        mockMvc.perform(post("/api/cron/lines").header("X-Cron-Secret", "letmein"))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * A declined call is still recorded. "Turned off" and "the schedule is not
     * calling at all" look identical on the admin page otherwise, and they
     * need completely different fixes.
     */
    @Test
    void recordsThatItWasCalledEvenWhenTurnedOff() throws Exception {
        mockMvc.perform(post("/api/cron/lines").header("X-Cron-Secret", "letmein"))
                .andExpect(status().isServiceUnavailable());

        assertThat(cronJobs.findById(CronJob.LINES))
                .get()
                .satisfies(job -> {
                    assertThat(job.getLastRunAt()).isNotNull();
                    assertThat(job.getLastStatus()).isEqualTo(CronJob.Status.SKIPPED);
                });
    }

    /** An unauthorised call is not a run, and must not be recorded as one. */
    @Test
    void doesNotRecordAnUnauthorisedCall() throws Exception {
        mockMvc.perform(post("/api/cron/lines").header("X-Cron-Secret", "wrong"))
                .andExpect(status().isUnauthorized());

        assertThat(cronJobs.findById(CronJob.LINES))
                .get()
                .satisfies(job -> assertThat(job.getLastRunAt()).isNull());
    }
}
