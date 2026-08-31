package com.nickspicks.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A member. The id is the Supabase auth user id (the JWT "sub" claim) rather
 * than one we generate, so there is no mapping table to keep in sync.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    /** What they are called. Duplicates are fine - two people can be Nick. */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Who they are: the unique @handle. Separate from the display name because
     * one field could not be both a readable name and a stable identifier -
     * uniqueness made the second Nick pick something else, and handle-safety
     * banned the space in "Nick A".
     */
    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ColorTheme theme = ColorTheme.MIDNIGHT;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_mode", nullable = false)
    private ColorMode colorMode = ColorMode.DARK;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(UUID id, String email, String displayName, String username) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ColorTheme getTheme() {
        return theme;
    }

    public void setTheme(ColorTheme theme) {
        this.theme = theme;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
