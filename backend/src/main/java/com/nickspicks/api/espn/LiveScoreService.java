package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Live scores, clock and possession, read from ESPN's public scoreboard.
 *
 * <p>Deliberately not persisted. The stored score is the graded one and comes
 * from CollegeFootballData when a game finishes; this is a view over the same
 * game while it is being played. Keeping it out of the database means a
 * flapping third party can never corrupt a settled pick, and there is no
 * migration to own.
 *
 * <p>Refreshed at most every fifteen seconds and shared across every member on
 * the site, so a busy Saturday costs four calls a minute in total.
 */
@Service
public class LiveScoreService {

    /** Fast enough that a clock looks live, slow enough to be a good guest. */
    private static final Duration TTL = Duration.ofSeconds(15);

    private final EspnSiteClient espn;

    public LiveScoreService(EspnSiteClient espn) {
        this.espn = espn;
    }

    /**
     * A game in progress, as ESPN currently has it.
     *
     * @param state            ESPN's own bucket: {@code pre}, {@code in} or
     *                         {@code post}
     * @param period           1-4 in regulation, higher in overtime
     * @param periodLabel      "1st", "2nd", "OT" - ESPN's wording, not ours
     * @param clock            time remaining in the period, "4:21"
     * @param detail           ESPN's own one-line summary of the game state
     * @param possessionTeamId which team has the ball, null between plays and
     *                         on games where ESPN does not track it
     * @param downDistance     "2nd & 7 at OSU 34", when available
     */
    public record LiveGame(
            long gameId,
            String state,
            Integer homeScore,
            Integer awayScore,
            Integer period,
            String periodLabel,
            String clock,
            String detail,
            Integer possessionTeamId,
            String downDistance,
            boolean redZone) {

        public boolean inProgress() {
            return "in".equals(state);
        }
    }

    /**
     * Every game ESPN currently lists, keyed by game id.
     *
     * <p>Callers are expected to ask only when one of their games could
     * plausibly be underway - there is no point spending a call to be told
     * that Tuesday is quiet.
     */
    public Map<Long, LiveGame> current() {
        Map<Long, LiveGame> result = new HashMap<>();

        espn.scoreboard(TTL).ifPresent(board -> {
            for (JsonNode event : board.path("events")) {
                LiveGame game = toLiveGame(event);
                if (game != null) {
                    result.put(game.gameId(), game);
                }
            }
        });

        return result;
    }

    private LiveGame toLiveGame(JsonNode event) {
        long id;
        try {
            id = Long.parseLong(event.path("id").asText());
        } catch (NumberFormatException ex) {
            return null;
        }

        JsonNode competition = event.path("competitions").path(0);
        JsonNode status = competition.path("status").isMissingNode()
                ? event.path("status")
                : competition.path("status");
        JsonNode type = status.path("type");
        JsonNode situation = competition.path("situation");

        Integer home = null;
        Integer away = null;
        for (JsonNode competitor : competition.path("competitors")) {
            Integer score = parseInt(competitor.path("score").asText(null));
            if ("home".equals(competitor.path("homeAway").asText())) {
                home = score;
            } else if ("away".equals(competitor.path("homeAway").asText())) {
                away = score;
            }
        }

        return new LiveGame(
                id,
                text(type, "state"),
                home,
                away,
                status.path("period").isNumber() ? status.path("period").asInt() : null,
                periodLabel(status.path("period")),
                text(status, "displayClock"),
                text(type, "shortDetail"),
                parseInt(text(situation, "possession")),
                text(situation, "downDistanceText"),
                situation.path("isRedZone").asBoolean(false));
    }

    /**
     * "1st" through "4th", then "OT" and "2OT". ESPN sends the number only, and
     * a bare "5" beside a clock reads as a fifth quarter rather than overtime.
     */
    private String periodLabel(JsonNode period) {
        if (!period.isNumber() || period.asInt() <= 0) {
            return null;
        }
        int value = period.asInt();
        return switch (value) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            case 4 -> "4th";
            case 5 -> "OT";
            default -> (value - 4) + "OT";
        };
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
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
