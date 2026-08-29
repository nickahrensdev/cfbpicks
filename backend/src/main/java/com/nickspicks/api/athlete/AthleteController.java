package com.nickspicks.api.athlete;

import com.nickspicks.api.espn.EspnClient;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/athletes")
public class AthleteController {

    private final AthleteRepository athletes;
    private final DtoMapper mapper;
    private final EspnClient espn;

    public AthleteController(AthleteRepository athletes, DtoMapper mapper, EspnClient espn) {
        this.athletes = athletes;
        this.mapper = mapper;
        this.espn = espn;
    }

    /**
     * Athletes are only in our tables once someone has opened their team's
     * page, which is what pulls the roster. A 404 here means "nobody has
     * looked at that team yet", so the UI links through the team.
     */
    @GetMapping("/{id}")
    public ApiDtos.AthleteDetail detail(@PathVariable String id) {
        Athlete latest = athletes.findFirstByIdOrderBySeasonDesc(id)
                .orElseThrow(() -> new NotFoundException("Athlete %s not found".formatted(id)));

        List<ApiDtos.AthleteSeason> seasons = athletes.findAllByIdOrderBySeasonDesc(id).stream()
                .map(a -> new ApiDtos.AthleteSeason(a.getSeason(), a.getTeamSchool(), a.getTeamId(),
                        a.getPosition()))
                .toList();

        return new ApiDtos.AthleteDetail(
                latest.getId(), latest.getFirstName(), latest.getLastName(), latest.getPosition(),
                latest.getJersey(), latest.getHeight(), latest.getWeight(), latest.getYear(),
                latest.getHomeCity(), latest.getHomeState(), latest.getHomeCountry(),
                latest.getSeason(), mapper.teamSummary(latest.getTeamId()), seasons,
                // Enrichment, not a dependency: a player with no ESPN record
                // still gets a page, just a thinner one.
                espn.athlete(id).orElse(null));
    }
}
