package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import com.nickspicks.api.athlete.Athlete;
import com.nickspicks.api.athlete.AthleteRepository;
import com.nickspicks.api.cfbd.CfbdSyncRepository;
import com.nickspicks.api.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Rosters, from ESPN rather than CFBD.
 *
 * <p>This replaced the CFBD roster fetch for one reason above the others: it
 * was the only user-triggered call against a metered API. Opening a team page
 * spent one of a thousand monthly calls, and a member clicking through the
 * league could spend hundreds. ESPN is unmetered, so the same page view now
 * costs nothing that has to be budgeted.
 *
 * <p>The swap is possible at all because CFBD uses ESPN's ids for both teams
 * and athletes, so nothing has to be mapped and every stored athlete row keeps
 * its identity - existing links to /athletes/:id go on working.
 *
 * <p>Every field the CFBD roster supplied has an equivalent here, and ESPN
 * populates them more completely: on a sample squad all of name, jersey,
 * height, weight, class year and hometown came back for every player.
 */
@Service
public class EspnRosterService {

    private static final Logger log = LoggerFactory.getLogger(EspnRosterService.class);

    /** A player's height does not change during a season. */
    private static final Duration TTL = Duration.ofHours(12);

    private static final String RESOURCE = "roster";

    private final EspnSiteClient espn;
    private final AthleteRepository athletes;
    /**
     * The same marker table the CFBD ingest uses. Deliberately shared rather
     * than duplicated: a team already synced from CFBD this season must not be
     * re-fetched from ESPN just because the provider changed, and the row
     * means the same thing either way - "this team's roster is loaded".
     */
    private final CfbdSyncRepository syncs;

    public EspnRosterService(EspnSiteClient espn, AthleteRepository athletes,
                             CfbdSyncRepository syncs) {
        this.espn = espn;
        this.athletes = athletes;
        this.syncs = syncs;
    }

    /**
     * Fetches a roster the first time it is asked for and not again that
     * season. Called lazily from the team detail page.
     *
     * @return how many players were stored, or 0 if it was already synced or
     *         ESPN had nothing
     */
    @Transactional
    public int ensureRoster(Team team, int season) {
        String key = team.getId() + ":" + season;
        if (syncs.isSynced(RESOURCE, key)) {
            return 0;
        }
        int stored = refreshRoster(team, season);
        if (stored > 0) {
            // Only a fetch that produced players counts as synced. An ESPN
            // outage must leave the team to be tried again rather than
            // marking it done with an empty roster.
            syncs.markSynced(RESOURCE, key);
        }
        return stored;
    }

    /** The same fetch, unconditionally - for an admin re-run. */
    @Transactional
    public int refreshRoster(Team team, int season) {
        Optional<JsonNode> body = espn.roster(team.getId(), TTL);
        if (body.isEmpty()) {
            log.debug("No ESPN roster for {} ({})", team.getSchool(), team.getId());
            return 0;
        }

        JsonNode players = body.get().path("team").path("athletes");
        // Guards the shape rather than trusting it: this is an undocumented
        // API, and a silent change here would otherwise wipe nothing but also
        // store nothing, with the sync marker hiding it.
        if (!players.isArray() || players.isEmpty()) {
            log.warn("ESPN roster for {} had no athletes array", team.getSchool());
            return 0;
        }

        // ESPN can list the same player twice across position groups; the
        // (id, season) key would fail on the second insert before the first
        // has flushed.
        Set<String> seen = new HashSet<>();
        int stored = 0;

        for (JsonNode player : players) {
            String id = text(player, "id");
            if (id == null || !seen.add(id)) {
                continue;
            }

            Athlete athlete = athletes.findById(new Athlete.Key(id, season))
                    .orElseGet(Athlete::new);

            athlete.setId(id);
            athlete.setSeason(season);
            athlete.setFirstName(text(player, "firstName"));
            athlete.setLastName(text(player, "lastName"));
            athlete.setTeamId(team.getId());
            athlete.setTeamSchool(team.getSchool());
            athlete.setPosition(text(player.path("position"), "abbreviation"));
            athlete.setJersey(intOf(player, "jersey"));
            // Both already in the units the column documents - inches and
            // pounds - so they are rounded rather than converted.
            athlete.setHeight(rounded(player, "height"));
            athlete.setWeight(rounded(player, "weight"));
            athlete.setYear(intOf(player.path("experience"), "years"));

            // ESPN calls this birthPlace where CFBD called it hometown. For
            // all but a handful of players they are the same town, and it is
            // the only field either provider has for where someone is from.
            JsonNode from = player.path("birthPlace");
            athlete.setHomeCity(text(from, "city"));
            athlete.setHomeState(text(from, "state"));
            athlete.setHomeCountry(text(from, "country"));
            athlete.setUpdatedAt(Instant.now());

            athletes.save(athlete);
            stored++;
        }
        return stored;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null
                : value.asText();
    }

    /** ESPN sends the jersey as a string and the class year as a number. */
    private Integer intOf(JsonNode node, String field) {
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

    /** Height and weight arrive as decimals - 77.0 inches, 210.0 pounds. */
    private Integer rounded(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? (int) Math.round(value.asDouble()) : null;
    }
}
