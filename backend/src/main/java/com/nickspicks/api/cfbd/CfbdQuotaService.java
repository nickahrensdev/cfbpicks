package com.nickspicks.api.cfbd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The account's real CFBD quota, from {@code /info} rather than a hardcoded
 * assumption - a live check while building this found the account is
 * actually Tier 1 (5,000 calls/month), not the 1,000/month free tier the
 * admin page used to assume.
 *
 * <p>Fetched fresh on every ask. {@code /info} does not count against the
 * quota - verified by reading it twice three seconds apart and getting the
 * same usedCalls both times - so the 24-hour throttle this used to carry was
 * protecting a budget it could not spend, at the cost of showing an admin a
 * number that could be a day old at the moment they most wanted it accurate.
 *
 * <p>The snapshot row stays, and is still written on every refresh. It is
 * what answers when CFBD cannot be reached, which is the only case a stored
 * copy was ever needed for - see {@link CfbdQuotaSnapshot}.
 */
@Service
public class CfbdQuotaService {

    private static final Logger log = LoggerFactory.getLogger(CfbdQuotaService.class);

    private final CfbdClient cfbd;
    private final CfbdQuotaSnapshotRepository snapshots;

    public CfbdQuotaService(CfbdClient cfbd, CfbdQuotaSnapshotRepository snapshots) {
        this.cfbd = cfbd;
        this.snapshots = snapshots;
    }

    @Transactional
    public CfbdQuotaSnapshot current() {
        CfbdQuotaSnapshot snapshot = snapshots.findById((short) 1).orElse(null);

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
