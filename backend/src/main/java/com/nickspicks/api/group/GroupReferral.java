package com.nickspicks.api.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Who brought this member to the site.
 *
 * <p>Keyed by the person referred, so there is exactly one for ever. Whoever's
 * link first brought them keeps the credit - a link they follow next month
 * does not overwrite it, which is what stops referral counts being farmable by
 * passing links around an existing membership.
 */
@Entity
@Table(name = "group_referral")
public class GroupReferral {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** The group whose link they followed. */
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sharer_id", nullable = false)
    private UUID sharerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GroupReferral() {
    }

    public GroupReferral(UUID userId, UUID groupId, UUID sharerId) {
        this.userId = userId;
        this.groupId = groupId;
        this.sharerId = sharerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getSharerId() {
        return sharerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
