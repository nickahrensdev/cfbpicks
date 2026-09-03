package com.nickspicks.api.ingest;

import com.nickspicks.api.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Records what a data load did, since the load itself now runs off the
 * request thread - see {@code AsyncIngestService}. {@code start} commits
 * in its own transaction (REQUIRES_NEW) so the RUNNING row is visible to
 * the Data log tab immediately, before the background task that will
 * eventually finish it has even begun.
 */
@Service
public class DataLoadLogService {

    private final DataLoadLogRepository logs;

    public DataLoadLogService(DataLoadLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataLoadLog start(DataLoadLog.Kind kind, Integer season, String parts, Integer teamId,
                             AppUser admin) {
        DataLoadLog log = new DataLoadLog();
        log.setKind(kind);
        log.setSeason(season);
        log.setParts(parts);
        log.setTeamId(teamId);
        log.setTriggeredBy(admin.getId());
        log.setTriggeredByName(admin.getDisplayName());
        return logs.save(log);
    }

    /**
     * A run nobody pressed a button for.
     *
     * <p>triggered_by_name is not null - every row until now was written by a
     * person - so a scheduled run carries a label rather than a null, and no
     * reader of the log needs a special case for it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataLoadLog startForCron(DataLoadLog.Kind kind, Integer season) {
        DataLoadLog log = new DataLoadLog();
        log.setKind(kind);
        log.setSeason(season);
        log.setTriggeredByName("Scheduled");
        return logs.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long id, String resultSummary) {
        logs.findById(id).ifPresent(log -> {
            log.setStatus(DataLoadLog.Status.SUCCESS);
            log.setResultSummary(resultSummary);
            log.setFinishedAt(Instant.now());
            logs.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, String errorMessage) {
        logs.findById(id).ifPresent(log -> {
            log.setStatus(DataLoadLog.Status.FAILURE);
            // Truncated to fit the column - the message that matters for a
            // glance at the log is the start of it, not the full stack.
            log.setErrorMessage(
                    errorMessage == null ? null
                            : errorMessage.substring(0, Math.min(1000, errorMessage.length())));
            log.setFinishedAt(Instant.now());
            logs.save(log);
        });
    }

    @Transactional(readOnly = true)
    public List<DataLoadLog> recent() {
        return logs.findTop200ByOrderByStartedAtDesc();
    }
}
