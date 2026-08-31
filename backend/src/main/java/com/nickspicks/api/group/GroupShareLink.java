package com.nickspicks.api.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * One person's durable link to one group.
 *
 * <p>Durable is the point. A link someone has already pasted into a message has
 * to keep working, so pressing Share again returns the link they already have
 * rather than minting a new one and quietly breaking the old.
 */
@Entity
@Table(name = "group_share_link")
public class GroupShareLink {

    /**
     * 18 random bytes, url-safe. Long enough that guessing one is not a way
     * into a private group, short enough to paste into a message without the
     * link looking like a mistake.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sharer_id", nullable = false)
    private UUID sharerId;

    @Column(name = "token", nullable = false, length = 32)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GroupShareLink() {
    }

    public GroupShareLink(UUID groupId, UUID sharerId) {
        this.groupId = groupId;
        this.sharerId = sharerId;
        this.token = newToken();
    }

    private static String newToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getSharerId() {
        return sharerId;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
