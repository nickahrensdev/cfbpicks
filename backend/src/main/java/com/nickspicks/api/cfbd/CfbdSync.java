package com.nickspicks.api.cfbd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Records that a given slice of reference data has been fetched, so visiting a
 * team page a second time never costs a second API call.
 */
@Entity
@Table(name = "cfbd_sync")
@IdClass(CfbdSync.Key.class)
public class CfbdSync {

    @Id
    private String resource;

    @Id
    @Column(name = "sync_key")
    private String syncKey;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    protected CfbdSync() {
    }

    public CfbdSync(String resource, String syncKey) {
        this.resource = resource;
        this.syncKey = syncKey;
    }

    public String getResource() {
        return resource;
    }

    public String getSyncKey() {
        return syncKey;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void touch() {
        this.syncedAt = Instant.now();
    }

    /** Composite key for {@link CfbdSync}. */
    public static class Key implements Serializable {

        private String resource;
        private String syncKey;

        public Key() {
        }

        public Key(String resource, String syncKey) {
            this.resource = resource;
            this.syncKey = syncKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(resource, key.resource)
                    && Objects.equals(syncKey, key.syncKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resource, syncKey);
        }
    }
}
