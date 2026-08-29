package com.nickspicks.api.security;

import com.nickspicks.api.config.AppProperties;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.user.Role;
import com.nickspicks.api.web.ForbiddenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
            return users.save(new AppUser(id, email == null ? "" : email, defaultDisplayName(jwt, email, id)));
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
     * Prefer a name the member set during signup, fall back to the local part
     * of their email, and finally to a slice of their id. Uniqueness is
     * enforced by an index, so collisions get a numeric suffix.
     */
    private String defaultDisplayName(Jwt jwt, String email, UUID id) {
        String candidate = jwt.getClaimAsString("user_metadata.display_name");

        if (!StringUtils.hasText(candidate) && StringUtils.hasText(email)) {
            candidate = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = "member-" + id.toString().substring(0, 8);
        }
        if (candidate.length() > 55) {
            candidate = candidate.substring(0, 55);
        }

        String unique = candidate;
        int suffix = 2;
        while (users.existsByDisplayNameIgnoreCase(unique)) {
            unique = candidate + "-" + suffix++;
        }
        return unique;
    }
}
