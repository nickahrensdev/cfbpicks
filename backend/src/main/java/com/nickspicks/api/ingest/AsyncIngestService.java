package com.nickspicks.api.ingest;

import com.nickspicks.api.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Runs each manual data load off the request thread, so the admin Data page
 * gets an immediate response instead of holding the button (and the
 * request) for however long CFBD takes to answer. {@link DataLoadLogService}
 * is what lets the admin see it happened - see the Data log tab.
 *
 * <p>Each method here is called through this bean (never {@code this.}) so
 * Spring's {@code @Async} proxy actually intercepts it. Every one of them
 * ends the log row it was handed - a load that throws still has to leave a
 * FAILURE row rather than an eternally RUNNING one.
 */
@Service
public class AsyncIngestService {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestService.class);

    private final GameIngestService gameIngest;
    private final ReferenceIngestService referenceIngest;
    private final DataLoadLogService logs;

    public AsyncIngestService(GameIngestService gameIngest, ReferenceIngestService referenceIngest,
                              DataLoadLogService logs) {
        this.gameIngest = gameIngest;
        this.referenceIngest = referenceIngest;
        this.logs = logs;
    }

    @Async
    public void runReference(Long logId, int season, Set<String> parts) {
        try {
            StringBuilder summary = new StringBuilder();
            if (parts.contains("calendar")) {
                summary.append("calendar: ").append(referenceIngest.ingestCalendar(season)).append(" weeks. ");
            }
            if (parts.contains("teams")) {
                summary.append("teams: ").append(referenceIngest.ingestTeams(season)).append(". ");
            }
            if (parts.contains("coaches")) {
                summary.append("coaches: ").append(referenceIngest.ingestCoaches(season)).append(". ");
            }
            if (parts.contains("records")) {
                summary.append("records: ").append(referenceIngest.ingestRecords(season)).append(". ");
            }
            logs.succeed(logId, summary.toString().trim());
        } catch (Exception ex) {
            log.warn("Reference ingest {} failed", logId, ex);
            logs.fail(logId, ex.getMessage());
        }
    }

    @Async
    public void runGames(Long logId, int season) {
        try {
            int games = gameIngest.ingestSchedule(season);
            int lines = gameIngest.ingestLines(season);
            logs.succeed(logId, "%d games, %d lines updated".formatted(games, lines));
        } catch (Exception ex) {
            log.warn("Schedule ingest {} failed", logId, ex);
            logs.fail(logId, ex.getMessage());
        }
    }

    @Async
    public void runScores(Long logId, int season) {
        try {
            int graded = gameIngest.ingestScores(season);
            logs.succeed(logId, "%d games graded".formatted(graded));
        } catch (Exception ex) {
            log.warn("Score ingest {} failed", logId, ex);
            logs.fail(logId, ex.getMessage());
        }
    }

    @Async
    public void runRankings(Long logId, int season) {
        try {
            int rows = referenceIngest.ingestRankings(season);
            logs.succeed(logId, "%d ranking rows".formatted(rows));
        } catch (Exception ex) {
            log.warn("Rankings ingest {} failed", logId, ex);
            logs.fail(logId, ex.getMessage());
        }
    }

    @Async
    public void runRoster(Long logId, Team team, int season) {
        try {
            int players = referenceIngest.refreshRoster(team, season);
            logs.succeed(logId, "%d players".formatted(players));
        } catch (Exception ex) {
            log.warn("Roster ingest {} failed", logId, ex);
            logs.fail(logId, ex.getMessage());
        }
    }
}
