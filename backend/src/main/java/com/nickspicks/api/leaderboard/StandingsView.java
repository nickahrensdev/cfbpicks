package com.nickspicks.api.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only mapping over the v_standings SQL view. The ranking rule lives in
 * the view, so changing it is a migration rather than a code change.
 */
@Entity
@Immutable
@Table(name = "v_standings")
@IdClass(StandingsView.Key.class)
public class StandingsView {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    private Integer season;

    @Column(name = "display_name")
    private String displayName;

    private long wins;
    private long losses;
    private long pushes;

    @Column(name = "games_graded")
    private long gamesGraded;

    public UUID getUserId() {
        return userId;
    }

    public Integer getSeason() {
        return season;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getWins() {
        return wins;
    }

    public long getLosses() {
        return losses;
    }

    public long getPushes() {
        return pushes;
    }

    public long getGamesGraded() {
        return gamesGraded;
    }

    /** Composite key for {@link StandingsView}. */
    public static class Key implements Serializable {

        private UUID userId;
        private Integer season;

        public Key() {
        }

        public Key(UUID userId, Integer season) {
            this.userId = userId;
            this.season = season;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(season, key.season);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, season);
        }
    }
}
