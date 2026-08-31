package com.nickspicks.api.espn;

import com.fasterxml.jackson.databind.JsonNode;
import com.nickspicks.api.web.ApiDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rosters and player profiles, read from ESPN and never stored.
 *
 * <p>Nothing here writes. The athlete table this used to fill existed only
 * because CFBD charged per roster call, so the answer had to be kept; ESPN is
 * unmetered and {@link EspnSiteClient} already caches, which is the same
 * freshness a stored copy gave without the copy silently freezing for a season.
 *
 * <p>A roster is therefore always current - a transfer or a corrected jersey
 * shows up on the next page view rather than never.
 */
@Service
public class EspnRosterService {

    private static final Logger log = LoggerFactory.getLogger(EspnRosterService.class);

    /**
     * A player's height does not change during a season, so this is long. It is
     * what keeps a team page from making a request per view while still being
     * short enough that a mid-season change appears the same day.
     */
    private static final Duration TTL = Duration.ofHours(12);

    private final EspnSiteClient site;
    private final EspnClient core;

    public EspnRosterService(EspnSiteClient site, EspnClient core) {
        this.site = site;
        this.core = core;
    }

    /**
     * One team's current squad.
     *
     * <p>Empty rather than an exception when ESPN is unreachable: a roster is
     * one tab of a page that has a schedule and a staff list to show, and a
     * provider outage should cost the tab rather than the page.
     */
    public List<ApiDtos.AthleteSummary> roster(int teamId, ApiDtos.TeamSummary team) {
        Optional<JsonNode> body = site.roster(teamId, TTL);
        if (body.isEmpty()) {
            log.debug("No ESPN roster for team {}", teamId);
            return List.of();
        }

        JsonNode players = body.get().path("team").path("athletes");
        // Guards the shape rather than trusting it. This is an undocumented
        // API; a silent change would otherwise render an empty roster that
        // looks exactly like a team nobody has data for.
        if (!players.isArray() || players.isEmpty()) {
            log.warn("ESPN roster for team {} had no athletes array", teamId);
            return List.of();
        }

        // ESPN can list one player under two position groups.
        Set<String> seen = new HashSet<>();
        List<ApiDtos.AthleteSummary> roster = new ArrayList<>();

        for (JsonNode player : players) {
            String id = EspnJson.text(player, "id");
            if (id == null || !seen.add(id)) {
                continue;
            }
            roster.add(new ApiDtos.AthleteSummary(
                    id,
                    EspnJson.text(player, "firstName"),
                    EspnJson.text(player, "lastName"),
                    EspnJson.text(player.path("position"), "abbreviation"),
                    EspnJson.intOf(player, "jersey"),
                    EspnJson.intOf(player.path("experience"), "years"),
                    EspnJson.text(player.path("headshot"), "href"),
                    team));
        }

        // Jersey order, unnumbered players last - the order a printed roster
        // uses, and the one a reader scanning for a number expects.
        roster.sort(java.util.Comparator.comparing(ApiDtos.AthleteSummary::jersey,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return roster;
    }

    /**
     * One player, from ESPN's athlete resource rather than a roster.
     *
     * <p>Addressable directly by id, so a player page works from a link
     * without knowing which team to ask about first.
     */
    public Optional<EspnDtos.EspnAthlete> athlete(String athleteId) {
        return core.athlete(athleteId);
    }
}
