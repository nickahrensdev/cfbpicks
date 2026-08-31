package com.nickspicks.api.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone asking to join a group that requires approval.
 *
 * <p>One row per person per group, reused rather than accumulated - asking
 * again after a refusal moves this row back to {@code PENDING}. An owner then
 * sees a queue of people waiting rather than a log of every attempt.
 */
@Entity
@Table(name = "group_join_request")
public class GroupJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    protected GroupJoinRequest() {
    }

    public GroupJoinRequest(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    /** Puts an old, decided request back in the queue. */
    public void reopen() {
        this.status = JoinRequestStatus.PENDING;
        this.requestedAt = Instant.now();
        this.decidedAt = null;
        this.decidedBy = null;
    }

    public void decide(JoinRequestStatus outcome, UUID decider) {
        this.status = outcome;
        this.decidedAt = Instant.now();
        this.decidedBy = decider;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }
}
