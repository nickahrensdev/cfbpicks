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
 * ESPN's public core API, used to enrich what CollegeFootballData gives us.
 *
 * <p>No key, no published quota and no contract. Two consequences shape this
 * class:
 *
 * <ul>
 *   <li>Every failure is swallowed and returns empty. ESPN is a garnish on a
 *       page that already works without it - a timeout must not turn a team
 *       page into an error.
 *   <li>Responses are parsed as {@link JsonNode} rather than bound to records.
 *       Fields appear and vanish without notice, so a missing one has to read
 *       as null, not as a deserialisation failure.
 * </ul>
 *
 * <p>Results are cached in memory for an hour. Player biographies and stadium
 * names do not change within a session, and the cache means opening the same
 * team page twice costs one call, not two.
 *
 * <p>The ids line up because CollegeFootballData uses ESPN's own team and
 * athlete ids, so no cross-reference table is needed.
 */
@Component
public class EspnClient {

    private static final Logger log = LoggerFactory.getLogger(EspnClient.class);

    private static final String BASE_URL =
            "https://sports.core.api.espn.com/v2/sports/football/leagues/college-football";

    private static final Duration TTL = Duration.ofHours(1);

    /**
     * Short on purpose. This call sits inside a page load, so a slow third
     * party should be dropped rather than waited on.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Optional<JsonNode> value, Instant expiresAt) {
    }

    public EspnClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                // See EspnSiteClient for why this is a plain curl string
                // rather than a custom or browser-styled one.
                .defaultHeader("User-Agent", "curl/8.7.1")
                .build();
    }

    /** One player's ESPN profile, or empty if they have none or ESPN is down. */
    public Optional<EspnDtos.EspnAthlete> athlete(String athleteId) {
        return fetch("/athletes/" + athleteId).map(this::toAthlete);
    }

    /** One program's ESPN record, or empty if unknown or ESPN is down. */
    public Optional<EspnDtos.EspnTeam> team(int teamId) {
        return fetch("/teams/" + teamId).map(this::toTeam);
    }

    private EspnDtos.EspnAthlete toAthlete(JsonNode node) {
        JsonNode position = node.path("position");
        JsonNode birth = node.path("birthPlace");
        JsonNode experience = node.path("experience");

        return new EspnDtos.EspnAthlete(
                text(node, "id"),
                text(node, "displayName"),
                text(node, "shortName"),
                text(node, "jersey"),
                text(position, "displayName"),
                text(position, "abbreviation"),
                text(node, "displayHeight"),
                text(node, "displayWeight"),
                number(node, "age"),
                text(node, "dateOfBirth"),
                text(birth, "city"),
                text(birth, "state"),
                text(birth, "country"),
                text(experience, "displayValue"),
                number(experience, "years"),
                text(node.path("headshot"), "href"),
                text(node.path("flag"), "href"),
                node.path("active").asBoolean(false),
                text(node.path("status"), "name"),
                link(node, "playercard"),
                // Parsed from the $ref rather than followed - the id is
                // already in the URL, so resolving it would be a second call
                // for something we can read here.
                EspnJson.teamIdFromRef(node));
    }

    private EspnDtos.EspnTeam toTeam(JsonNode node) {
        JsonNode venue = node.path("venue");
        JsonNode address = venue.path("address");

        return new EspnDtos.EspnTeam(
                text(node, "id"),
                text(node, "displayName"),
                text(node, "shortDisplayName"),
                text(node, "nickname"),
                text(node, "location"),
                text(node, "name"),
                text(node, "abbreviation"),
                hex(text(node, "color")),
                hex(text(node, "alternateColor")),
                logo(node, "default"),
                logo(node, "dark"),
                node.path("isActive").asBoolean(false),
                text(venue, "fullName"),
                text(address, "city"),
                text(address, "state"),
                venue.path("indoor").asBoolean(false),
                venue.path("grass").asBoolean(false),
                image(venue),
                link(node, "clubhouse"));
    }

    private Optional<JsonNode> fetch(String path) {
        CacheEntry cached = cache.get(path);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }

        Optional<JsonNode> result;
        try {
            JsonNode body = restClient.get().uri(path).retrieve().body(JsonNode.class);
            result = body == null || body.isMissingNode() ? Optional.empty() : Optional.of(body);
        } catch (RuntimeException ex) {
            // Including a 404: plenty of our athletes predate ESPN's coverage
            // or simply are not there. Nothing to report to the member.
            log.debug("ESPN lookup {} failed: {}", path, ex.toString());
            result = Optional.empty();
        }

        // Misses are cached too, so a team ESPN does not know is not looked up
        // again on every page view.
        cache.put(path, new CacheEntry(result, Instant.now().plus(TTL)));
        return result;
    }

    /** The first link whose {@code rel} includes the given role. */
    private String link(JsonNode node, String rel) {
        for (JsonNode candidate : node.path("links")) {
            for (JsonNode role : candidate.path("rel")) {
                if (rel.equals(role.asText())) {
                    return text(candidate, "href");
                }
            }
        }
        return null;
    }

    /** The first logo tagged with the given variant - "default" or "dark". */
    private String logo(JsonNode node, String variant) {
        for (JsonNode candidate : node.path("logos")) {
            for (JsonNode role : candidate.path("rel")) {
                if (variant.equals(role.asText())) {
                    return text(candidate, "href");
                }
            }
        }
        return null;
    }

    private String image(JsonNode venue) {
        JsonNode images = venue.path("images");
        return images.isArray() && !images.isEmpty() ? text(images.get(0), "href") : null;
    }

    /** ESPN sends colours bare; CSS needs the hash. */
    private String hex(String value) {
        return value == null || value.startsWith("#") ? value : "#" + value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText();
    }

    private Integer number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asInt();
    }
}
