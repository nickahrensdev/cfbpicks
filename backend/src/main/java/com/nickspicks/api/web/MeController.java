package com.nickspicks.api.web;

import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.user.ColorMode;
import com.nickspicks.api.user.ColorTheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first call the UI makes after sign-in. It provisions the member row as
 * a side effect, so nothing else has to worry about whether one exists.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    /** Both fields cap here. Shared with the provisioning path. */
    public static final int NAME_MAX = 20;

    /**
     * The @handle, and the only unique one. No spaces, because it renders as
     * {@code @nick} and a space in one reads as the end of the name rather
     * than part of it.
     *
     * <p>Shared with {@link com.nickspicks.api.security.CurrentUserService},
     * which seeds the first username and has to satisfy the same rule.
     */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*[A-Za-z0-9]$";

    /**
     * What they are called. Free-form on purpose: two people can both be
     * "Nick", and a display name is allowed the space that a handle is not.
     */
    public record DisplayNameRequest(
            @NotBlank
            @Size(min = 2, max = NAME_MAX)
            String displayName) {
    }

    public record UsernameRequest(
            @NotBlank
            @Size(min = 2, max = NAME_MAX)
            @Pattern(regexp = USERNAME_PATTERN,
                    message = "Use letters, numbers, dots, dashes or underscores - no spaces")
            String username) {
    }

    private final CurrentUserService currentUser;
    private final AppUserRepository users;

    public MeController(CurrentUserService currentUser, AppUserRepository users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping
    public ApiDtos.MemberProfile me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.resolve(jwt);
        return profile(user);
    }

    /** Nothing to check but the shape - display names are not unique. */
    @PutMapping
    @Transactional
    public ApiDtos.MemberProfile updateDisplayName(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisplayNameRequest request) {

        AppUser user = currentUser.resolve(jwt);
        user.setDisplayName(request.displayName().trim());
        users.save(user);
        return profile(user);
    }

    @PutMapping("/username")
    @Transactional
    public ApiDtos.MemberProfile updateUsername(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UsernameRequest request) {

        AppUser user = currentUser.resolve(jwt);
        String wanted = request.username().trim();

        // Case-insensitive uniqueness, matching the database index. Comparing
        // against their current one first lets someone re-case their own.
        if (!wanted.equalsIgnoreCase(user.getUsername())
                && users.existsByUsernameIgnoreCase(wanted)) {
            throw new UsernameTakenException("That username is already taken");
        }

        user.setUsername(wanted);
        users.save(user);
        return profile(user);
    }

    /** No uniqueness or format concerns here, so it stays a separate, narrowly-validated endpoint. */
    public record ThemeRequest(@NotNull ColorTheme theme, @NotNull ColorMode colorMode) {
    }

    @PutMapping("/theme")
    @Transactional
    public ApiDtos.MemberProfile updateTheme(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody ThemeRequest request) {
        AppUser user = currentUser.resolve(jwt);
        user.setTheme(request.theme());
        user.setColorMode(request.colorMode());
        users.save(user);
        return profile(user);
    }

    private ApiDtos.MemberProfile profile(AppUser user) {
        return new ApiDtos.MemberProfile(user.getId(), user.getDisplayName(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getTheme().name(), user.getColorMode().name());
    }

    /** 409 rather than a validation error - the name is well-formed, just taken. */
    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException(String message) {
            super(message);
        }
    }
}
