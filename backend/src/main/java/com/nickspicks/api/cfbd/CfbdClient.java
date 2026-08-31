package com.nickspicks.api.cfbd;

import com.nickspicks.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the CollegeFootballData API.
 *
 * <p>Every call is logged so quota consumption is visible, and a monthly
 * ceiling stops a runaway loop from burning the whole allowance. The account
 * this runs against is Tier 1 (5,000 calls/month, confirmed live via
 * {@code /info}) - the true number is what {@link #info()} reports, this
 * ceiling is only an internal safety stop set comfortably under it.
 */
@Component
public class CfbdClient {

    private static final Logger log = LoggerFactory.getLogger(CfbdClient.class);

    /** Leaves headroom under the real 5,000/month Tier 1 allowance. */
    private static final long MONTHLY_CALL_CEILING = 4500;

    /**
     * A bound is still wanted - RestClient sets none of its own, and a hung
     * response should not wedge a load forever - but it has to be generous.
     *
     * <p>This was 10s, which broke the season-wide {@code /lines} load: the
     * response is a healthy 364KB in under a second from a desktop, but on
     * Render's free tier the JVM is CPU-throttled, and that load calls
     * {@code /lines} straight after writing ~888 games one row at a time.
     * Starved of CPU, a socket read stalls past 10s and the call dies with an
     * I/O error having never read a byte of a perfectly good response.
     *
     * <p>The short timeout was originally added to stop a per-page-view ATS
     * refresh hanging the game details page. Nothing on a request path fetches
     * ATS any more (see {@code TeamAtsService}), so that pressure is gone and
     * the ceiling can go back up to where a slow-but-working load survives.
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);

    private final RestClient restClient;
    private final AppProperties properties;
    private final CfbdCallLogRepository callLog;
    private final CfbdCallRecorder recorder;

    public CfbdClient(AppProperties properties, CfbdCallLogRepository callLog,
                      CfbdCallRecorder recorder) {
        this.properties = properties;
        this.callLog = callLog;
        this.recorder = recorder;

        int timeoutSeconds = properties.getCfbd().getTimeoutSeconds();
        Duration timeout = timeoutSeconds > 0
                ? Duration.ofSeconds(timeoutSeconds)
                : DEFAULT_TIMEOUT;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getCfbd().getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getCfbd().getApiKey())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public boolean isConfigured() {
        return !properties.getCfbd().getApiKey().isBlank();
    }

    public List<CfbdDtos.CalendarWeek> calendar(int year) {
        return get("/calendar", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Every program in every division - 684 of them for 2026, in one call.
     * Same cost as /teams/fbs, so there is no reason to fetch less.
     */
    public List<CfbdDtos.TeamDto> allTeams(int year) {
        return get("/teams", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Every game in a season - 888 for 2026 across 14 weeks, in one call.
     *
     * <p>Asking per week costs one call each for the same data, so the whole
     * season is both cheaper and gives members weeks to look ahead at. Season
     * type is left off for the same reason - unfiltered returns everything,
     * postseason included.
     */
    public List<CfbdDtos.GameDto> games(int year) {
        return get("/games",
                Map.of("year", year,
                        "classification", properties.getCfbd().getClassification()),
                new ParameterizedTypeReference<>() {
                });
    }

    /** Every poll for every week of a season, in one call. */
    public List<CfbdDtos.RankingWeekDto> rankings(int year) {
        return get("/rankings", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Every posted line in a season, in one call.
     *
     * <p>Year only. Narrowing by week or season type returns a subset of the
     * same rows for the same one call, so asking week by week just costs a call
     * per week to learn less.
     */
    public List<CfbdDtos.LineDto> lines(int year) {
        return get("/lines", Map.of("year", year),
                new ParameterizedTypeReference<>() {
                });
    }

    /**
     * Coaches for a season.
     *
     * <p>FBS only. This is not a filter we apply - the provider simply has no
     * coach records for FCS programs, and asking for one by name returns an
     * empty array. There is deliberately no per-team variant: it would spend
     * a call per FCS team page to fetch nothing.
     */
    public List<CfbdDtos.CoachDto> coaches(int year) {
        return get("/coaches", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Season-long win/loss splits for every team - overall, conference,
     * home/away/neutral, regular season vs. postseason - in one call.
     */
    public List<CfbdDtos.RecordDto> records(int year) {
        return get("/records", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Every team's against-the-spread record for the season so far, in one
     * call. Cost is identical whether one team or the whole league is
     * needed, so a refresh always upserts everyone - see
     * {@code TeamAtsService}, which is what decides when this is worth
     * calling at all.
     */
    public List<CfbdDtos.AtsDto> teamAts(int year) {
        return get("/teams/ats", Map.of("year", year), new ParameterizedTypeReference<>() {
        });
    }

    /**
     * All-time head-to-head history between two programs, by school name -
     * this endpoint has no team-id parameter. A single object, not a list.
     */
    public CfbdDtos.MatchupDto matchup(String team1, String team2) {
        return getOne("/teams/matchup", Map.of("team1", team1, "team2", team2),
                CfbdDtos.MatchupDto.class);
    }

    /**
     * The account's real quota state - monthly limit, used, remaining, reset
     * date. Costs a call like anything else; see {@code CfbdQuotaService} for
     * why this is not fetched on every request.
     */
    public CfbdDtos.InfoDto info() {
        return getOne("/info", Map.of(), CfbdDtos.InfoDto.class);
    }

    /** Calls used so far in the trailing 30 days. */
    public long callsThisMonth() {
        return callLog.countSince(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    /** Shared quota guard and URI-building for both the list and single-object callers. */
    private String checkedTarget(String path, Map<String, Object> params) {
        if (!isConfigured()) {
            throw new CfbdUnavailableException("No CollegeFootballData API key configured");
        }

        long used = callsThisMonth();
        if (used >= MONTHLY_CALL_CEILING) {
            throw new CfbdUnavailableException(
                    "CFBD monthly call ceiling reached (%d of %d) - refusing to call %s"
                            .formatted(used, MONTHLY_CALL_CEILING, path));
        }

        // Encoded, not concatenated. A team like "Texas A&M" carries an
        // ampersand that would otherwise end the query string early - the
        // request still succeeds, it just silently asks about "Texas A" and
        // comes back empty.
        UriComponentsBuilder display = UriComponentsBuilder.fromPath(path);
        params.forEach(display::queryParam);
        String target = display.build().encode().toUriString();

        log.info("CFBD GET {} (call {} of {} this month)", target, used + 1, MONTHLY_CALL_CEILING);
        return target;
    }

    private <T> List<T> get(String path, Map<String, Object> params,
                            ParameterizedTypeReference<List<T>> type) {
        String target = checkedTarget(path, params);

        try {
            List<T> body = restClient.get()
                    // The builder form applies the base URL and encodes each
                    // value; passing a pre-built string would re-expand any
                    // braces in it as URI template variables.
                    .uri(builder -> {
                        builder.path(path);
                        params.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .retrieve()
                    .body(type);
            recorder.record(target, 200);
            return body == null ? List.of() : body;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            recorder.record(target, ex.getStatusCode().value());
            throw new CfbdUnavailableException(
                    "CFBD call to %s failed: %s".formatted(path, ex.getStatusCode()), ex);
        } catch (CfbdUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recorder.record(target, null);
            throw new CfbdUnavailableException("CFBD call to %s failed".formatted(path), ex);
        }
    }

    private <T> T getOne(String path, Map<String, Object> params, Class<T> type) {
        String target = checkedTarget(path, params);

        try {
            T body = restClient.get()
                    .uri(builder -> {
                        builder.path(path);
                        params.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .retrieve()
                    .body(type);
            recorder.record(target, 200);
            if (body == null) {
                throw new CfbdUnavailableException("CFBD call to %s returned no body".formatted(path));
            }
            return body;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            recorder.record(target, ex.getStatusCode().value());
            throw new CfbdUnavailableException(
                    "CFBD call to %s failed: %s".formatted(path, ex.getStatusCode()), ex);
        } catch (CfbdUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            recorder.record(target, null);
            throw new CfbdUnavailableException("CFBD call to %s failed".formatted(path), ex);
        }
    }
}
