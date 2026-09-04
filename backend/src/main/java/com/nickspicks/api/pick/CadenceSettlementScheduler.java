package com.nickspicks.api.pick;

import com.nickspicks.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes out finished periods on a timer.
 *
 * <p>Hourly rather than tied to the score ingest, because a shortfall does not
 * depend on any score - it is decided the moment the period's last game kicks
 * off, and a group can have minimums to settle in a week where the ingest is
 * turned off entirely.
 *
 * <p>Carries its own {@code @EnableScheduling} for that reason. Every other
 * scheduler in the app is conditional - the two cron jobs are
 * {@code @Profile("prod")} - and settlement must not quietly stop working
 * because the class that happened to switch scheduling on was not loaded.
 *
 * <p>Assumes a single instance - add ShedLock before running more than one.
 */
@Component
@EnableScheduling
public class CadenceSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(CadenceSettlementScheduler.class);

    private final CadenceSettlementService settlement;
    private final AppProperties properties;

    public CadenceSettlementScheduler(CadenceSettlementService settlement,
                                      AppProperties properties) {
        this.settlement = settlement;
        this.properties = properties;
    }

    @Scheduled(cron = "0 20 * * * *", zone = "America/Chicago")
    public void settle() {
        try {
            int closed = settlement.settleAll(properties.getPickem().getSeason());
            if (closed > 0) {
                log.info("Settled {} closed period(s)", closed);
            }
        } catch (RuntimeException ex) {
            // Never kill the scheduler thread - the next hour tries again.
            log.error("Cadence settlement failed", ex);
        }
    }
}
