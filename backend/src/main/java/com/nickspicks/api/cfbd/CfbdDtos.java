package com.nickspicks.api.cfbd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response shapes from the CollegeFootballData API, confirmed against live
 * responses. Only the fields this app uses are mapped; the rest are ignored.
 */
public final class CfbdDtos {

    private CfbdDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalendarWeek(
            Integer season,
            Integer week,
            String seasonType,
            Instant startDate,
            Instant endDate,
            Instant firstGameStart,
            Instant lastGameStart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamDto(
            Integer id,
            String school,
            String mascot,
            String abbreviation,
            String conference,
            String division,
            String classification,
            String color,
            String alternateColor,
            List<String> logos,
            String twitter,
            Location location) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Location(
                String name,
                String city,
                String state,
                Integer capacity) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameDto(
            Long id,
            Integer season,
            Integer week,
            String seasonType,
            Instant startDate,
            Boolean startTimeTBD,
            Boolean completed,
            Boolean neutralSite,
            Boolean conferenceGame,
            String venue,
            Integer homeId,
            String homeTeam,
            String homeConference,
            Integer homePoints,
            Integer homePregameElo,
            Integer awayId,
            String awayTeam,
            String awayConference,
            Integer awayPoints,
            Integer awayPregameElo,
            /** Postgame - null until the game finishes. */
            BigDecimal homePostgameWinProbability,
            BigDecimal awayPostgameWinProbability,
            BigDecimal excitementIndex) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineDto(
            Long id,
            Integer season,
            Integer week,
            Integer homeTeamId,
            String homeTeam,
            Integer awayTeamId,
            String awayTeam,
            List<LineEntry> lines) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record LineEntry(
                String provider,
                BigDecimal spread,
                String formattedSpread,
                BigDecimal spreadOpen,
                BigDecimal overUnder,
                BigDecimal overUnderOpen,
                Integer homeMoneyline,
                Integer awayMoneyline) {
        }
    }

    /** One week of polls. /rankings?year=N returns the whole season. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingWeekDto(
            Integer season,
            Integer week,
            String seasonType,
            List<PollDto> polls) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PollDto(String poll, List<RankDto> ranks) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RankDto(
                Integer rank,
                Integer teamId,
                String school,
                String conference,
                Integer firstPlaceVotes,
                Integer points) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RosterPlayerDto(
            String id,
            String firstName,
            String lastName,
            String team,
            Integer weight,
            Integer height,
            Integer jersey,
            Integer year,
            String position,
            String homeCity,
            String homeState,
            String homeCountry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoachDto(
            Integer id,
            String firstName,
            String lastName,
            Instant hireDate,
            List<CoachSeasonDto> seasons) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CoachSeasonDto(
                Integer teamId,
                String school,
                String conference,
                Integer year,
                Integer games,
                Integer wins,
                Integer losses,
                Integer ties,
                BigDecimal spOverall,
                BigDecimal spOffense,
                BigDecimal spDefense) {
        }
    }
}
