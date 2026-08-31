package com.nickspicks.api.web;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two names a member carries: a free-form display name that may repeat, and
 * a unique @handle. They exist separately because neither rule works for both -
 * making the display name unique means telling someone their own name is taken,
 * and letting the handle repeat means it cannot identify anyone.
 */
class IdentityIntegrationTest extends IntegrationTest {

    private static final UUID ALICE = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @Autowired
    private AppUserRepository users;

    @Override
    protected void cleanUp() {
        users.deleteAll();
    }

    /**
     * Supabase nests what the signup form collected under user_metadata, so
     * this is also the guard on reading it as an object rather than as a
     * top-level "user_metadata.username" key that no token actually has.
     */
    @Test
    void provisioningTakesBothNamesFromTheSignupMetadata() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice Smith", "alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Smith"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    /** No metadata at all: the email's local part seeds both. */
    @Test
    void fallsBackToTheEmailWhenSignupSentNoNames() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "solo@example.com", null, null)))
                .andExpect(jsonPath("$.displayName").value("solo"))
                .andExpect(jsonPath("$.username").value("solo"));
    }

    /**
     * A chosen handle is coerced rather than rejected. This runs on the very
     * first request of a new account, and Supabase validated nothing - refusing
     * here would lock someone out over punctuation.
     */
    @Test
    void sanitisesAHandleThatSignupNeverValidated() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice", "Alice Smith!")))
                .andExpect(jsonPath("$.username").value("Alice_Smith"));
    }

    /** Two people, one wanted handle - the second gets a suffix, not an error. */
    @Test
    void makesACollidingHandleUniqueAtProvisioningTime() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice", "nick")))
                .andExpect(jsonPath("$.username").value("nick"));

        mockMvc.perform(get("/api/me").with(user(BOB, "bob@example.com", "Bob", "nick")))
                .andExpect(jsonPath("$.username").value("nick2"));
    }

    @Test
    void twoMembersMayShareADisplayNameButNotAUsername() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Nick", "alice")));
        mockMvc.perform(get("/api/me").with(user(BOB, "bob@example.com", "Bob", "bob")));

        // The same display name is fine - that is the whole point of the split.
        mockMvc.perform(put("/api/me").with(user(BOB, "bob@example.com", "Bob", "bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Nick\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nick"))
                .andExpect(jsonPath("$.username").value("bob"));

        // The handle is not, and case is not a way around it.
        mockMvc.perform(put("/api/me/username").with(user(BOB, "bob@example.com", "Bob", "bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ALICE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    /** Uniqueness ignores case, so re-casing your own handle must still work. */
    @Test
    void aMemberMayRecaseTheirOwnUsername() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice", "alice")));

        mockMvc.perform(put("/api/me/username").with(user(ALICE, "alice@example.com", "Alice", "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"Alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Alice"));
    }

    /**
     * A rename is validated where provisioning sanitises: here the member is
     * present and can be told, so a bad handle is a message rather than a
     * silent rewrite of what they typed.
     */
    @Test
    void rejectsASpaceInARenamedUsername() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice", "alice")));

        mockMvc.perform(put("/api/me/username").with(user(ALICE, "alice@example.com", "Alice", "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"alice smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Both names cap at 20, so neither can overflow the column or the table cell. */
    @Test
    void rejectsNamesOverTwentyCharacters() throws Exception {
        mockMvc.perform(get("/api/me").with(user(ALICE, "alice@example.com", "Alice", "alice")));

        mockMvc.perform(put("/api/me").with(user(ALICE, "alice@example.com", "Alice", "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"123456789012345678901\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me/username").with(user(ALICE, "alice@example.com", "Alice", "alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"123456789012345678901\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A Supabase-shaped token: the signup form's fields arrive nested under
     * user_metadata, not as top-level claims.
     */
    private RequestPostProcessor user(UUID id, String email, String displayName, String username) {
        return jwt().jwt(builder -> {
            builder.subject(id.toString())
                    .claim("email", email)
                    .audience(List.of("authenticated"));
            if (displayName != null || username != null) {
                builder.claim("user_metadata", metadata(displayName, username));
            }
        });
    }

    private Map<String, Object> metadata(String displayName, String username) {
        if (displayName == null) {
            return Map.of("username", username);
        }
        if (username == null) {
            return Map.of("display_name", displayName);
        }
        return Map.of("display_name", displayName, "username", username);
    }
}
