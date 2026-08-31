package com.nickspicks.api.athlete;

import com.nickspicks.api.espn.EspnDtos;
import com.nickspicks.api.espn.EspnRosterService;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Player pages, read live from ESPN.
 *
 * <p>Nothing about an athlete is stored. The table that used to back this
 * existed because CFBD charged per roster call and the answer had to be kept;
 * with an unmetered provider the page is simply asked for when it is opened,
 * which also means a transfer or a corrected jersey is right rather than
 * frozen at whenever the roster was first pulled.
 */
@RestController
@RequestMapping("/api/athletes")
public class AthleteController {

    private final EspnRosterService rosters;
    private final DtoMapper mapper;

    public AthleteController(EspnRosterService rosters, DtoMapper mapper) {
        this.rosters = rosters;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public ApiDtos.AthleteDetail detail(@PathVariable String id) {
        EspnDtos.EspnAthlete espn = rosters.athlete(id)
                .orElseThrow(() -> new NotFoundException("Athlete %s not found".formatted(id)));

        return new ApiDtos.AthleteDetail(
                espn.id(),
                firstName(espn),
                lastName(espn),
                espn.positionAbbreviation() != null ? espn.positionAbbreviation() : espn.position(),
                parse(espn.jersey()),
                inches(espn.displayHeight()),
                pounds(espn.displayWeight()),
                espn.experienceYears(),
                espn.birthCity(),
                espn.birthState(),
                espn.birthCountry(),
                espn.headshotUrl(),
                // The team is ours - it is ingested separately and is not
                // athlete data. Null when ESPN names a team we do not carry.
                espn.teamId() == null ? null : mapper.teamSummary(espn.teamId()),
                espn);
    }

    /**
     * ESPN gives one display name here where the roster gives two fields, so
     * the split is on the last space - "Amorion Walker" but also
     * "Sonny Styles Jr." keeping the surname whole enough to read.
     */
    private String firstName(EspnDtos.EspnAthlete espn) {
        String name = espn.displayName();
        if (name == null) {
            return null;
        }
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    private String lastName(EspnDtos.EspnAthlete espn) {
        String name = espn.displayName();
        if (name == null) {
            return null;
        }
        int space = name.indexOf(' ');
        return space < 0 ? null : name.substring(space + 1);
    }

    private Integer parse(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** {@code 6' 5"} to 77. The columns this replaced were in inches. */
    private Integer inches(String display) {
        if (display == null) {
            return null;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)'\\s*(\\d+)?").matcher(display);
        if (!matcher.find()) {
            return null;
        }
        int feet = Integer.parseInt(matcher.group(1));
        int rest = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return feet * 12 + rest;
    }

    /** {@code 210 lbs} to 210. */
    private Integer pounds(String display) {
        if (display == null) {
            return null;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)").matcher(display);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
