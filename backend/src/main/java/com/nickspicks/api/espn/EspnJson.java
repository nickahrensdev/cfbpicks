package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading ESPN's JSON defensively.
 *
 * <p>Every accessor here answers null rather than throwing or returning a
 * placeholder. The API is undocumented and inconsistent about types - a jersey
 * is a string, a height is a decimal, a team is a URL - so the shape is treated
 * as something to be checked rather than assumed.
 */
final class EspnJson {

    /** Pulls the numeric team id out of a $ref like ".../teams/194?lang=en". */
    private static final Pattern TEAM_REF = Pattern.compile("/teams/(\\d+)");

    private EspnJson() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText();
    }

    /** ESPN sends a jersey as a string and a class year as a number. */
    static Integer intOf(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.valueOf(value.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * The team an athlete belongs to, taken from the {@code $ref} URL ESPN
     * gives instead of an id. Following the link would be a second HTTP call
     * for a number already present in it.
     */
    static Integer teamIdFromRef(JsonNode athlete) {
        String ref = text(athlete.path("team"), "$ref");
        if (ref == null) {
            return null;
        }
        Matcher matcher = TEAM_REF.matcher(ref);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
