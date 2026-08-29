package com.nickspicks.api.cfbd;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate bean rather than a method on {@link CfbdClient}: a self-invoked
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String endpoint, Integer status) {
        callLog.save(new CfbdCallLog(endpoint, status));
    }
}
