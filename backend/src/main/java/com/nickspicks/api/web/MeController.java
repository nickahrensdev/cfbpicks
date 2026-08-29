package com.nickspicks.api.web;

import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    /**
     * Display names are what the leaderboard shows, so they have to be
     * unique. Letters, numbers and a few separators only - no leading or
     * trailing whitespace to make two names look identical.
     */
    public record DisplayNameRequest(
            @NotBlank
            @Size(min = 2, max = 40)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ._-]*[A-Za-z0-9]$",
                    message = "Use letters, numbers, spaces, dots, dashes or underscores")
            String displayName) {
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

    @PutMapping
    @Transactional
    public ApiDtos.MemberProfile updateDisplayName(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisplayNameRequest request) {

        AppUser user = currentUser.resolve(jwt);
        String wanted = request.displayName().trim();

        // Case-insensitive uniqueness, matching the database index. Comparing
        // against the current name first lets someone re-case their own.
        if (!wanted.equalsIgnoreCase(user.getDisplayName())
                && users.existsByDisplayNameIgnoreCase(wanted)) {
            throw new DisplayNameTakenException("That display name is already taken");
        }

        user.setDisplayName(wanted);
        users.save(user);
        return profile(user);
    }

    private ApiDtos.MemberProfile profile(AppUser user) {
        return new ApiDtos.MemberProfile(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole().name());
    }

    /** 409 rather than a validation error - the name is well-formed, just taken. */
    public static class DisplayNameTakenException extends RuntimeException {
        public DisplayNameTakenException(String message) {
            super(message);
        }
    }
}
