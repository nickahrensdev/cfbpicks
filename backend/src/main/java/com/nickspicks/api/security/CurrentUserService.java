package com.nickspicks.api.security;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.user.Role;
import com.nickspicks.api.web.ForbiddenException;
import com.nickspicks.api.web.MeController;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Maps a verified Supabase token to a local member row, creating it on first
 * sight. Supabase owns registration, so there is no signup endpoint here to
 * drift out of sync with it.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository users;
    private final AppProperties properties;

    public CurrentUserService(AppUserRepository users, AppProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public AppUser resolve(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());

        AppUser user = users.findById(id).orElseGet(() -> {
            String email = jwt.getClaimAsString("email");
            return users.save(new AppUser(id, email == null ? "" : email,
                    defaultDisplayName(jwt, email, id),
                    defaultUsername(jwt, email, id)));
        });

        // Bootstrap promotion: a configured admin email becomes ADMIN on any
        // request, so adding one to the config takes effect without touching
        // the database. One-way - demotion is an explicit admin-page action.
        if (user.getRole() != Role.ADMIN && isBootstrapAdmin(user.getEmail())) {
            user.setRole(Role.ADMIN);
            users.save(user);
        }
        return user;
    }

    public UUID resolveId(Jwt jwt) {
        return resolve(jwt).getId();
    }

    /** Resolves the caller and rejects anyone who is not an admin. */
    @Transactional
    public AppUser requireAdmin(Jwt jwt) {
        AppUser user = resolve(jwt);
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("This requires the admin role");
        }
        return user;
    }

    private boolean isBootstrapAdmin(String email) {
        return StringUtils.hasText(email)
                && properties.getAdminEmails().stream().anyMatch(email::equalsIgnoreCase);
    }

    /**
     * What they are called. Free-form, duplicates allowed - so unlike the
     * username there is nothing to make unique, only a length to respect.
     */
    private String defaultDisplayName(Jwt jwt, String email, UUID id) {
        String candidate = trimTo(metadata(jwt, "display_name"), MeController.NAME_MAX);

        if (!StringUtils.hasText(candidate) && StringUtils.hasText(email)) {
            candidate = trimTo(localPart(email), MeController.NAME_MAX);
        }
        return StringUtils.hasText(candidate)
                ? candidate
                : "member-" + id.toString().substring(0, 8);
    }

    /**
     * The unique @handle. Prefers what they chose at signup, falls back to the
     * local part of their email, then to a slice of their id.
     *
     * <p>The candidate is sanitised rather than rejected: a failed sign-in is a
     * terrible way to learn about a punctuation rule, and this runs on the very
     * first request of a brand new account. Supabase owns signup and validates
     * nothing, so without this the format rule would only ever apply to
     * renames - anyone could sign up with whatever they liked and keep it.
     */
    private String defaultUsername(Jwt jwt, String email, UUID id) {
        String candidate = sanitise(metadata(jwt, "username"));

        // Someone who signed up before usernames existed sent only a display
        // name; it is the closest thing to a handle they ever chose.
        if (!StringUtils.hasText(candidate)) {
            candidate = sanitise(metadata(jwt, "display_name"));
        }
        if (!StringUtils.hasText(candidate) && StringUtils.hasText(email)) {
            candidate = sanitise(localPart(email));
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = "member-" + id.toString().substring(0, 8);
        }

        // Leave room for a suffix before checking, so a collision does not push
        // the result past the limit.
        String base = trimTo(candidate, MeController.NAME_MAX - 2);
        String unique = base;
        int suffix = 2;
        while (users.existsByUsernameIgnoreCase(unique)) {
            unique = base + suffix++;
        }
        return unique;
    }

    /**
     * One field out of the token's {@code user_metadata} object - where
     * Supabase puts whatever the signup form collected.
     *
     * <p>It has to be read as a nested map: {@code getClaimAsString} looks up a
     * top-level key, so asking it for "user_metadata.display_name" finds
     * nothing and every name silently falls back to the email address.
     *
     * <p>The flat key is still tried afterwards, for a token that was minted
     * with the field at the top level rather than nested.
     */
    private String metadata(Jwt jwt, String key) {
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        return jwt.getClaimAsString("user_metadata." + key);
    }

    private String localPart(String email) {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    private String trimTo(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /**
     * Coerces a handle into the allowed shape.
     *
     * <p>Spaces become underscores so a two-part name survives as one handle;
     * anything else outside the set is dropped, and the result is trimmed of
     * leading and trailing separators so it satisfies the anchors in
     * {@link MeController#USERNAME_PATTERN}.
     */
    private String sanitise(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9._-]", "")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");

        // Two characters is the documented minimum; anything shorter falls
        // through to the next candidate rather than being padded.
        return cleaned.length() < 2 ? null : cleaned;
    }
}
