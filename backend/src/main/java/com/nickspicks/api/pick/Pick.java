package com.nickspicks.api.pick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pick")
public class Pick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The league this pick was made in. Part of the pick's identity - the same
     * member can play the same game in two groups, under two sets of rules.
     */
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Selection selection;

    /**
     * Which market this pick plays. Always derived from the selection via
     * {@link #setSelection}, never set independently.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    /**
     * The line as it stood when this pick was made or last edited - a spread
     * for a SPREAD pick, a total for a TOTAL one. Grading uses this, never the
     * game's current line, so later movement cannot change a pick a member
     * already committed to.
     *
     * <p>Null for a WINNER pick, which is played against no number at all. A
     * check constraint keeps the two in step.
     */
    @Column(name = "locked_line")
    private BigDecimal lockedLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickResult result = PickResult.PENDING;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Selection getSelection() {
        return selection;
    }

    /** Also sets the market, so the pair cannot drift apart. */
    public void setSelection(Selection selection) {
        this.selection = selection;
        this.market = selection == null ? null : selection.market();
    }

    public Market getMarket() {
        return market;
    }

    public BigDecimal getLockedLine() {
        return lockedLine;
    }

    public void setLockedLine(BigDecimal lockedLine) {
        this.lockedLine = lockedLine;
    }

    public PickResult getResult() {
        return result;
    }

    public void setResult(PickResult result) {
        this.result = result;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public void setGradedAt(Instant gradedAt) {
        this.gradedAt = gradedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
