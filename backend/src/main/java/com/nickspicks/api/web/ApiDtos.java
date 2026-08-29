package com.nickspicks.api.web;

import com.nickspicks.api.espn.EspnDtos;
import com.nickspicks.api.espn.EspnGameService;
import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.pick.Market;
import com.nickspicks.api.pick.PickResult;
import com.nickspicks.api.pick.Selection;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response and request shapes for the public API. */
public final class ApiDtos {

    private ApiDtos() {
    }

    // ---------------------------------------------------------------- teams

    public record TeamSummary(
            Integer id,
            String school,
            String mascot,
            String abbreviation,
            String conference,
            String logoUrl,
            String color,
            /**
             * Position in the highest-priority poll that published for the
             * week being viewed, or null if unranked. Rendered as "#1" before
             * the name.
             */
            Integer rank) {
    }

    public record TeamDetail(
            Integer id,
            String school,
            String mascot,
            String abbreviation,
            String conference,
            String division,
            String color,
            String alternateColor,
            String logoUrl,
            String twitter,
            String venueName,
            String venueCity,
            String venueState,
            Integer venueCapacity,
            List<CoachSummary> coaches,
            List<AthleteSummary> roster,
            List<GameSummary> schedule,
            /** Position in the poll that drives the rank shown by the name. */
            Integer rank,
            /** Every poll's current placement, so all three are visible here. */
            List<PollPlacement> currentRankings,
            /** Week-by-week placements across the season, newest first. */
            List<RankingHistoryWeek> rankingHistory,
            /** Branding and venue detail from ESPN. Null when unavailable. */
            EspnDtos.EspnTeam espn) {
    }

    public record PollPlacement(String poll, Integer week, Integer rank,
                                Integer firstPlaceVotes, Integer points) {
    }

    public record RankingHistoryWeek(Integer week, List<PollPlacement> placements) {
    }

    // ------------------------------------------------------------- athletes

    public record AthleteSummary(
            String id,
            String firstName,
            String lastName,
            String position,
            Integer jersey,
            Integer year,
            TeamSummary team) {
    }

    public record AthleteDetail(
            String id,
            String firstName,
            String lastName,
            String position,
            Integer jersey,
            Integer height,
            Integer weight,
            Integer year,
            String homeCity,
            String homeState,
            String homeCountry,
            Integer season,
            TeamSummary team,
            List<AthleteSeason> seasons,
            /** Biography from ESPN. Null when they have no record there. */
            EspnDtos.EspnAthlete espn) {
    }

    public record AthleteSeason(Integer season, String teamSchool, Integer teamId, String position) {
    }

    // --------------------------------------------------------------- coaches

    public record CoachSummary(Integer id, String firstName, String lastName, Instant hireDate) {
    }

    public record CoachDetail(
            Integer id,
            String firstName,
            String lastName,
            Instant hireDate,
            List<CoachSeasonRow> seasons) {
    }

    public record CoachSeasonRow(
            Integer season,
            String school,
            Integer teamId,
            String conference,
            Integer games,
            Integer wins,
            Integer losses,
            Integer ties,
            BigDecimal spOverall) {
    }

    // ----------------------------------------------------------------- games

    public record GameSummary(
            Long id,
            Integer season,
            Integer week,
            TeamSummary homeTeam,
            TeamSummary awayTeam,
            String homeTeamName,
            String awayTeamName,
            /** Always present, even when the team is not in our table. */
            String homeConference,
            String awayConference,
            Instant kickoff,
            boolean startTimeTbd,
            boolean neutralSite,
            String venue,
            BigDecimal homeSpread,
            /** Null when no total is posted, which is independent of the spread. */
            BigDecimal overUnder,
            String status,
            Integer homeScore,
            Integer awayScore,
            /**
             * True once the 30-minute window has closed. Shared by both
             * markets - the lock is a property of kickoff.
             */
            boolean locked,
            Instant locksAt,
            /** The caller's own picks on this game - at most one per market. */
            PickSummary mySpreadPick,
            PickSummary myTotalPick,
            /**
             * The current line is strictly better for the side the caller
             * took, so re-locking can only help. False when there is no pick.
             */
            boolean spreadLineImproved,
            boolean totalLineImproved,
            /**
             * Score, clock and possession while the game is being played.
             * Null at every other time, and never stored - the graded score
             * still comes from our own ingest.
             */
            LiveScoreService.LiveGame live) {
    }

    public record GameDetail(
            GameSummary game,
            BigDecimal spreadOpen,
            BigDecimal overUnder,
            BigDecimal overUnderOpen,
            Integer homeMoneyline,
            Integer awayMoneyline,
            String spreadProvider,
            Instant spreadUpdatedAt,
            Integer homePregameElo,
            Integer awayPregameElo,
            /** Postgame win probability, 0..1. Null until the game finishes. */
            BigDecimal homeWinProbability,
            BigDecimal awayWinProbability,
            BigDecimal excitementIndex,
            String homeConference,
            String awayConference,
            boolean conferenceGame,
            /** Every member's pick, only once the game has kicked off. */
            List<MemberPick> memberPicks,
            boolean picksRevealed,
            /** Box score, leaders and venue detail from ESPN. May be null. */
            EspnGameService.EspnGame espn) {
    }

    // ----------------------------------------------------------------- picks

    public record PickSummary(
            UUID id,
            Long gameId,
            Selection selection,
            Market market,
            BigDecimal lockedLine,
            PickResult result,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * A pick with its game.
     *
     * <p>{@code lineImproved} says the game's current spread is strictly
     * better for the side taken, so re-locking can only help. Decided on the
     * server so the sign logic lives in one place.
     */
    public record PickWithGame(PickSummary pick, GameSummary game, boolean lineImproved) {
    }

    public record MemberPick(
            UUID userId,
            String displayName,
            Selection selection,
            Market market,
            BigDecimal lockedLine,
            PickResult result) {
    }

    public record WeekPicks(
            Integer season,
            Integer week,
            Integer picksUsed,
            Integer picksRemaining,
            Integer maxPicks,
            List<PickWithGame> picks) {
    }

    /**
     * {@code expectedLine} is the number the page was showing. When present
     * and no longer current, the pick is rejected with LINE_MOVED rather than
     * silently placed at a number the member never saw.
     */
    /**
     * The market is derived from the selection, so there is no separate field
     * for it and no way to send a contradictory pair.
     */
    public record CreatePickRequest(
            @NotNull Long gameId,
            @NotNull Selection selection,
            BigDecimal expectedLine) {
    }

    public record UpdatePickRequest(
            @NotNull Selection selection,
            BigDecimal expectedLine) {
    }

    // ----------------------------------------------------------- leaderboard

    public record StandingsRow(
            Integer rank,
            UUID userId,
            String displayName,
            /** Every pick in the period, pending ones included. */
            long totalPicks,
            long wins,
            long losses,
            long pushes,
            Double winPercentage) {
    }

    // ------------------------------------------------------------------ misc

    public record CurrentWeek(Integer season, Integer week, List<Integer> availableWeeks,
                              List<Integer> loadedWeeks) {
    }

    /** Options for the games page filters, scoped to the week being viewed. */
    public record GameFilters(List<String> conferences, List<TeamSummary> teams,
                              Double maxSpread) {
    }

    public record MemberProfile(UUID id, String displayName, String email, String role) {
    }
}
