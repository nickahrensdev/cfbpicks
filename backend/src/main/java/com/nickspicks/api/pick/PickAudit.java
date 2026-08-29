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

/**
 * One row per pick action - create, update (including line re-locks) or
 * cancel. Insert-only: rows are never edited or deleted, and there is no FK
 * to pick so the history of a cancelled pick survives its deletion.
 */
@Entity
@Table(name = "pick_audit")
public class PickAudit {

    public enum Action { CREATE, UPDATE, CANCEL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pick_id", nullable = false)
    private UUID pickId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Selection selection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(name = "locked_line", nullable = false)
    private BigDecimal lockedLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_selection")
    private Selection previousSelection;

    @Column(name = "previous_locked_line")
    private BigDecimal previousLockedLine;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PickAudit() {
    }

    private PickAudit(Action action, Pick pick,
                      Selection previousSelection, BigDecimal previousLockedLine) {
        this.action = action;
        this.pickId = pick.getId();
        this.userId = pick.getUserId();
        this.gameId = pick.getGameId();
        this.selection = pick.getSelection();
        this.market = pick.getMarket();
        this.lockedLine = pick.getLockedLine();
        this.previousSelection = previousSelection;
        this.previousLockedLine = previousLockedLine;
    }

    public static PickAudit created(Pick pick) {
        return new PickAudit(Action.CREATE, pick, null, null);
    }

    public static PickAudit updated(Pick pick, Selection previousSelection,
                                    BigDecimal previousLockedLine) {
        return new PickAudit(Action.UPDATE, pick, previousSelection, previousLockedLine);
    }

    public static PickAudit cancelled(Pick pick) {
        return new PickAudit(Action.CANCEL, pick, null, null);
    }

    public Long getId() {
        return id;
    }

    public UUID getPickId() {
        return pickId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getGameId() {
        return gameId;
    }

    public Action getAction() {
        return action;
    }

    public Selection getSelection() {
        return selection;
    }

    public Market getMarket() {
        return market;
    }

    public BigDecimal getLockedLine() {
        return lockedLine;
    }

    public Selection getPreviousSelection() {
        return previousSelection;
    }

    public BigDecimal getPreviousLockedLine() {
        return previousLockedLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
