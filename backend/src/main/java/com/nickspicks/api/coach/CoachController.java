package com.nickspicks.api.coach;

import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.DtoMapper;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coaches")
public class CoachController {

    private final CoachRepository coaches;
    private final CoachSeasonRepository coachSeasons;
    private final DtoMapper mapper;

    public CoachController(CoachRepository coaches, CoachSeasonRepository coachSeasons,
                           DtoMapper mapper) {
        this.coaches = coaches;
        this.coachSeasons = coachSeasons;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public ApiDtos.CoachDetail detail(@PathVariable int id) {
        Coach coach = coaches.findById(id)
                .orElseThrow(() -> new NotFoundException("Coach %d not found".formatted(id)));

        List<ApiDtos.CoachSeasonRow> seasons =
                coachSeasons.findAllByCoachIdOrderBySeasonDesc(id).stream()
                        .map(mapper::coachSeasonRow)
                        .toList();

        return new ApiDtos.CoachDetail(coach.getId(), coach.getFirstName(), coach.getLastName(),
                coach.getHireDate(), seasons);
    }
}
