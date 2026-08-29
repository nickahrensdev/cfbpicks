package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The game-page supplement: what ESPN knows about one game that our own
 * ingest does not.
 *
 * <p>Everything here is read from a single {@code /summary} call and reshaped
 * into flat records. The raw response is a deep tree with several dozen
 * branches; passing it through untouched would put ESPN's shape - and its
 * freedom to change it - straight into the front end.
 */
@Service
public class EspnGameService {

    /**
     * Short enough that a game in progress keeps moving, long enough that a
     * finished game is not re-fetched for every visitor. The summary is mostly
     * box-score data, which only changes while the game is on.
     */
    private static final Duration TTL = Duration.ofSeconds(60);

    /** Box-score rows worth showing. ESPN sends about thirty; these are the ones
     * a reader scans for. Order is the order they render in. */
    private static final List<String> HEADLINE_STATS = List.of(
            "totalYards", "netPassingYards", "rushingYards", "firstDowns",
            "thirdDownEff", "turnovers", "possessionTime", "totalPenaltiesYards");

    private final EspnSiteClient espn;

    public EspnGameService(EspnSiteClient espn) {
        this.espn = espn;
    }

    public record Stat(String label, String value) {
    }

    public record TeamStats(Integer teamId, String team, String logoUrl, List<Stat> stats) {
    }

    /**
     * A statistical leader. {@code athleteId} matches our own athlete ids, so
     * the name links to their profile page like any other.
     */
    public record Leader(String category, String athleteId, String name, String position,
                         String headshotUrl, String value) {
    }

    public record TeamLeaders(Integer teamId, String team, List<Leader> leaders) {
    }

    public record AtsRecord(Integer teamId, String team, String summary) {
    }

    public record EspnGame(
            String venueName,
            String venueCity,
            String venueState,
            String venueImageUrl,
            boolean indoor,
            Integer attendance,
            /** Network or streaming service carrying the game. */
            String broadcast,
            List<TeamStats> teamStats,
            List<TeamLeaders> leaders,
            List<AtsRecord> againstTheSpread,
            /**
             * Home team's live win probability, 0..1, from ESPN's in-game
             * model. Present while a game is on, where the stored postgame
             * figure only arrives afterwards.
             */
            BigDecimal homeWinProbability) {
    }

    /** Empty when ESPN has nothing on this game, or is unreachable. */
    public Optional<EspnGame> summary(long gameId) {
        return espn.summary(gameId, TTL).map(this::toGame);
    }

    private EspnGame toGame(JsonNode node) {
        JsonNode venue = node.path("gameInfo").path("venue");
        JsonNode address = venue.path("address");

        return new EspnGame(
                text(venue, "fullName"),
                text(address, "city"),
                text(address, "state"),
                firstImage(venue),
                venue.path("indoor").asBoolean(false),
                node.path("gameInfo").path("attendance").isNumber()
                        ? node.path("gameInfo").path("attendance").asInt()
                        : null,
                broadcast(node),
                teamStats(node),
                leaders(node),
                againstTheSpread(node),
                homeWinProbability(node));
    }

    private String broadcast(JsonNode node) {
        JsonNode first = node.path("broadcasts").path(0);
        return text(first, "station") != null
                ? text(first, "station")
                : text(first.path("media"), "shortName");
    }

    private List<TeamStats> teamStats(JsonNode node) {
        List<TeamStats> result = new ArrayList<>();

        for (JsonNode side : node.path("boxscore").path("teams")) {
            JsonNode team = side.path("team");
            List<Stat> stats = new ArrayList<>();

            for (String wanted : HEADLINE_STATS) {
                for (JsonNode stat : side.path("statistics")) {
                    if (wanted.equals(stat.path("name").asText())) {
                        String value = text(stat, "displayValue");
                        if (value != null) {
                            stats.add(new Stat(text(stat, "label"), value));
                        }
                        break;
                    }
                }
            }

            if (!stats.isEmpty()) {
                result.add(new TeamStats(intOf(team, "id"), text(team, "displayName"),
                        text(team, "logo"), stats));
            }
        }

        return result;
    }

    private List<TeamLeaders> leaders(JsonNode node) {
        List<TeamLeaders> result = new ArrayList<>();

        for (JsonNode side : node.path("leaders")) {
            JsonNode team = side.path("team");
            List<Leader> found = new ArrayList<>();

            for (JsonNode category : side.path("leaders")) {
                // One name per category - ESPN lists several, and a game page
                // wants the leader, not the depth chart.
                JsonNode entry = category.path("leaders").path(0);
                JsonNode athlete = entry.path("athlete");
                if (athlete.isMissingNode()) {
                    continue;
                }
                found.add(new Leader(
                        text(category, "displayName"),
                        text(athlete, "id"),
                        text(athlete, "displayName"),
                        text(athlete.path("position"), "abbreviation"),
                        text(athlete.path("headshot"), "href"),
                        text(entry, "displayValue")));
            }

            if (!found.isEmpty()) {
                result.add(new TeamLeaders(intOf(team, "id"), text(team, "displayName"), found));
            }
        }

        return result;
    }

    private List<AtsRecord> againstTheSpread(JsonNode node) {
        List<AtsRecord> result = new ArrayList<>();

        for (JsonNode side : node.path("againstTheSpread")) {
            JsonNode team = side.path("team");
            JsonNode record = side.path("records").path(0);
            String summary = text(record, "summary");
            if (summary != null) {
                result.add(new AtsRecord(intOf(team, "id"), text(team, "displayName"), summary));
            }
        }

        return result;
    }

    /**
     * The last entry in ESPN's play-by-play win probability, which is the
     * current one. Null before kickoff, when the array is empty.
     */
    private BigDecimal homeWinProbability(JsonNode node) {
        JsonNode series = node.path("winprobability");
        if (!series.isArray() || series.isEmpty()) {
            return null;
        }
        JsonNode last = series.get(series.size() - 1).path("homeWinPercentage");
        return last.isNumber()
                ? BigDecimal.valueOf(last.asDouble()).setScale(4, RoundingMode.HALF_UP)
                : null;
    }

    private String firstImage(JsonNode venue) {
        JsonNode images = venue.path("images");
        return images.isArray() && !images.isEmpty() ? text(images.get(0), "href") : null;
    }

    private Integer intOf(JsonNode node, String field) {
        String value = text(node, field);
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText();
    }
}
