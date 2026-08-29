package com.nickspicks.api.cfbd;

import com.nickspicks.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the CollegeFootballData API.
 *
 * <p>Every call is logged so quota consumption is visible, and a monthly
 * ceiling stops a runaway loop from burning the whole allowance. The free tier
 * is 1,000 calls a month.
 */
@Component
public class CfbdClient {

    private static final Logger log = LoggerFactory.getLogger(CfbdClient.class);

    /** Leaves headroom under the 1,000/month free tier. */
    private static final long MONTHLY_CALL_CEILING = 900;

    private final RestClient restClient;
    private final AppProperties properties;
    private final CfbdCallLogRepository callLog;
    private final CfbdCallRecorder recorder;

    public CfbdClient(AppProperties properties, CfbdCallLogRepository callLog,
                      CfbdCallRecorder recorder) {
        this.properties = properties;
        this.callLog = callLog;
        this.recorder = recorder;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getCfbd().getBaseUrl())
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

    public List<CfbdDtos.RosterPlayerDto> roster(String team, int year) {
        return get("/roster", Map.of("team", team, "year", year),
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

    /** Calls used so far in the trailing 30 days. */
    public long callsThisMonth() {
        return callLog.countSince(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private <T> List<T> get(String path, Map<String, Object> params,
                            ParameterizedTypeReference<List<T>> type) {
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
}
