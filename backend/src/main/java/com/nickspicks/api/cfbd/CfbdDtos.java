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

    /** Season win/loss splits for one team. /records?year=N returns every team. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecordDto(
            Integer year,
            Integer teamId,
            String team,
            String classification,
            String conference,
            String division,
            BigDecimal expectedWins,
            Splits total,
            Splits conferenceGames,
            Splits homeGames,
            Splits awayGames,
            Splits neutralSiteGames,
            Splits regularSeason,
            Splits postseason) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Splits(Integer games, Integer wins, Integer losses, Integer ties) {
        }
    }

    /** One team's against-the-spread record for the season so far. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AtsDto(
            Integer year,
            Integer teamId,
            String team,
            String conference,
            Integer games,
            Integer atsWins,
            Integer atsLosses,
            Integer atsPushes,
            BigDecimal avgCoverMargin) {
    }

    /**
     * All-time head-to-head history between two schools, by name - this
     * endpoint has no team-id parameter and returns one object, not a list.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchupDto(
            String team1,
            String team2,
            Integer team1Wins,
            Integer team2Wins,
            Integer ties,
            List<MatchupGameDto> games) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MatchupGameDto(
                Integer season,
                Integer week,
                String seasonType,
                Instant date,
                Boolean neutralSite,
                String venue,
                String homeTeam,
                Integer homeScore,
                String awayTeam,
                Integer awayScore,
                String winner) {
        }
    }

    /** The account's real quota state. One object, not a list. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InfoDto(
            Integer monthlyLimit,
            Integer usedCalls,
            Integer remainingCalls,
            Instant resetAt) {
    }
}
