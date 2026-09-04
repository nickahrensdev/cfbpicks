package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ESPN's site API - the one that backs espn.com's scoreboard and game pages.
 *
 * <p>Separate from {@link EspnClient} because it is a different host with
 * different shapes and, more importantly, a different freshness requirement: a
 * live scoreboard is stale in seconds where a player's height is good for a
 * year. Each caller passes the time-to-live it needs.
 *
 * <p>Same failure policy as the core client: everything is best-effort and
 * every error returns empty. Live scores are a nicety layered over the stored
 * schedule, and a page must render without them.
 */
@Component
public class EspnSiteClient {

    private static final Logger log = LoggerFactory.getLogger(EspnSiteClient.class);

    private static final String BASE_URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/college-football";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * ESPN pages 25 events by default; a full Saturday is closer to a hundred.
     * {@code groups=80} is FBS, which is what members pick.
     */
    private static final String SCOREBOARD = "/scoreboard?limit=200&groups=80";

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Optional<JsonNode> value, Instant expiresAt) {
    }

    public EspnSiteClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                // ESPN's edge blocks anything it cannot place, and rejects the
                // default Java agent along with any custom or browser-styled
                // one that does not match the connection making the request.
                // A plain curl string is what passed testing and is what this
                // client is, in effect: a scripted reader of a public,
                // unauthenticated JSON endpoint.
                .defaultHeader("User-Agent", "curl/8.7.1")
                .build();
    }

    /**
     * Today's board, scores and clocks included.
     *
     * <p>No date parameter: "what is on now" is exactly the default, and it is
     * the only window in which live data means anything.
     */
    public Optional<JsonNode> scoreboard(Duration ttl) {
        return fetch(SCOREBOARD, ttl);
    }

    /**
     * One game's full detail. The event id is the CollegeFootballData game id -
     * CFBD carries ESPN's own ids, so no cross-reference is needed.
     */
    public Optional<JsonNode> summary(long gameId, Duration ttl) {
        return fetch("/summary?event=" + gameId, ttl);
    }

    /**
     * A team's current roster, in one call.
     *
     * <p>{@code ?enable=roster} on the team resource rather than the dedicated
     * {@code /roster} path, which silently caps at 100 players - Ohio State
     * returns 100 there and 120 here, so the shorter path drops a fifth of
     * every squad without saying so.
     *
     * <p>Current only. ESPN has no season parameter here, so a past year's
     * roster cannot be re-fetched; whatever was stored for it stays as it is.
     */
    public Optional<JsonNode> roster(int teamId, Duration ttl) {
        return fetch("/teams/" + teamId + "?enable=roster", ttl);
    }

    /**
     * Where ESPN thinks the season is: {@code season.year},
     * {@code season.type} and {@code week.number}.
     *
     * <p>The same scoreboard the live scores come from carries these, but it
     * is deliberately <em>not</em> reused. The cache is keyed by path, and
     * live scores are fetched with a fifteen-second TTL against a fifteen
     * minute one here - sharing the entry would let this staleness leak into
     * the scores. A different path is a different entry.
     *
     * <p>{@code limit=1} because the games are not wanted at all; only the
     * calendar position is, and it sits above the events.
     */
    public Optional<JsonNode> seasonPosition(Duration ttl) {
        return fetch("/scoreboard?limit=1&groups=80", ttl);
    }

    private Optional<JsonNode> fetch(String path, Duration ttl) {
        CacheEntry cached = cache.get(path);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }

        Optional<JsonNode> result;
        try {
            JsonNode body = restClient.get().uri(path).retrieve().body(JsonNode.class);
            result = body == null || body.isMissingNode() ? Optional.empty() : Optional.of(body);
        } catch (RuntimeException ex) {
            log.debug("ESPN site lookup {} failed: {}", path, ex.toString());
            result = Optional.empty();
        }

        cache.put(path, new CacheEntry(result, Instant.now().plus(ttl)));
        return result;
    }
}
