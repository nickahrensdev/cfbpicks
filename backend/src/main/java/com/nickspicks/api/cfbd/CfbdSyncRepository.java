package com.nickspicks.api.cfbd;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CfbdSyncRepository extends JpaRepository<CfbdSync, CfbdSync.Key> {

    default boolean isSynced(String resource, String syncKey) {
        return existsById(new CfbdSync.Key(resource, syncKey));
    }

    default void markSynced(String resource, String syncKey) {
        save(new CfbdSync(resource, syncKey));
    }
}
