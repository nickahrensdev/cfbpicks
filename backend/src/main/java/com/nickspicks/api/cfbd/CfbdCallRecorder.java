package com.nickspicks.api.cfbd;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the account has actually spent.
 *
 * <p>Separate bean rather than a method on {@link CfbdClient}: a self-invoked
 * {@code @Transactional} method does not go through Spring's proxy, so
 * REQUIRES_NEW would be silently ignored and a failed call would roll its own
 * log entry back.
 */
@Component
public class CfbdCallRecorder {

    private final CfbdCallLogRepository callLog;

    public CfbdCallRecorder(CfbdCallLogRepository callLog) {
        this.callLog = callLog;
    }

    /**
     * The quota endpoint, which is free.
     *
     * <p>Verified: two reads three seconds apart both reported the same
     * usedCalls. Logging it would make this table - and callsThisMonth(),
     * which counts it - climb for calls the provider never charges, and the
     * admin page now asks on every load rather than once a day.
     */
    private static final String FREE_ENDPOINT = "/info";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String endpoint, Integer status) {
        if (endpoint != null && endpoint.startsWith(FREE_ENDPOINT)) {
            return;
        }
        callLog.save(new CfbdCallLog(endpoint, status));
    }
}
