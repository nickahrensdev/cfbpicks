package com.nickspicks.api.cfbd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * The account's real CFBD quota, from {@code /info} rather than a hardcoded
 * assumption - a live check while building this found the account is
 * actually Tier 1 (5,000 calls/month), not the 1,000/month free tier the
 * admin page used to assume.
 *
 * <p>Refreshed at most once every 24 hours, checked against a row in the
 * database rather than an in-memory timestamp - see {@link CfbdQuotaSnapshot}
 * for why that distinction matters here.
 */
@Service
public class CfbdQuotaService {

    private static final Logger log = LoggerFactory.getLogger(CfbdQuotaService.class);
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(24);

    private final CfbdClient cfbd;
    private final CfbdQuotaSnapshotRepository snapshots;

    public CfbdQuotaService(CfbdClient cfbd, CfbdQuotaSnapshotRepository snapshots) {
        this.cfbd = cfbd;
        this.snapshots = snapshots;
    }

    @Transactional
    public CfbdQuotaSnapshot current() {
        CfbdQuotaSnapshot snapshot = snapshots.findById((short) 1).orElse(null);

        boolean stale = snapshot == null
                || snapshot.getFetchedAt().isBefore(Instant.now().minus(REFRESH_INTERVAL));

        if (!stale) {
            return snapshot;
        }

        try {
            CfbdDtos.InfoDto info = cfbd.info();
            if (snapshot == null) {
                snapshot = new CfbdQuotaSnapshot();
            }
            snapshot.setFetchedAt(Instant.now());
            snapshot.setMonthlyLimit(info.monthlyLimit());
            snapshot.setUsedCalls(info.usedCalls());
            snapshot.setRemainingCalls(info.remainingCalls());
            snapshot.setResetAt(info.resetAt());
            return snapshots.save(snapshot);
        } catch (CfbdUnavailableException ex) {
            // A stale-but-present number beats none at all; only truly the
            // first-ever check (no CFBD key configured yet, say) has nothing
            // to fall back on.
            log.warn("Could not refresh CFBD quota: {}", ex.getMessage());
            return snapshot;
        }
    }
}
