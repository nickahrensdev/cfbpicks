package com.nickspicks.api.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One member's place in one group. */
@Entity
@Table(name = "group_member")
@IdClass(GroupMember.Key.class)
public class GroupMember {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role = GroupRole.MEMBER;

    /**
     * Pinned to the top of this member's group picker. A property of the
     * membership, not the group - everyone else's view is unaffected.
     */
    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    protected GroupMember() {
    }

    public GroupMember(UUID groupId, UUID userId, GroupRole role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Composite key for {@link GroupMember}. */
    public static class Key implements Serializable {

        private UUID groupId;
        private UUID userId;

        public Key() {
        }

        public Key(UUID groupId, UUID userId) {
            this.groupId = groupId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(groupId, key.groupId)
                    && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId);
        }
    }
}
