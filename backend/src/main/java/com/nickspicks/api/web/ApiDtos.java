package com.nickspicks.api.web;

import com.nickspicks.api.espn.EspnDtos;
import com.nickspicks.api.espn.EspnGameService;
import com.nickspicks.api.espn.LiveScoreService;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.GroupRole;
import com.nickspicks.api.group.GroupSettings;
import com.nickspicks.api.group.GroupType;
import com.nickspicks.api.group.LengthType;
import com.nickspicks.api.group.Visibility;
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
            EspnDtos.EspnTeam espn,
            /** Season win/loss splits. Null until an admin loads /records. */
            RecordSummary record,
            /** Against-the-spread record for the season being viewed. Null until fetched. */
            AtsSummary ats,
            /** Every season this team has an ATS record for, newest first. */
            List<AtsSummary> atsHistory) {
    }

    public record PollPlacement(String poll, Integer week, Integer rank,
                                Integer firstPlaceVotes, Integer points) {
    }

    public record RankingHistoryWeek(Integer week, List<PollPlacement> placements) {
    }

    // ----------------------------------------------------------- records/ats

    public record WinLossSplit(Integer games, Integer wins, Integer losses, Integer ties) {
    }

    public record RecordSummary(
            BigDecimal expectedWins,
            WinLossSplit total,
            WinLossSplit conference,
            WinLossSplit home,
            WinLossSplit away,
            WinLossSplit neutral,
            WinLossSplit regularSeason,
            WinLossSplit postseason) {
    }

    /** One season's against-the-spread record. {@code season} labels the row in a history. */
    public record AtsSummary(Integer season, Integer games, Integer wins, Integer losses,
                             Integer pushes, BigDecimal avgCoverMargin) {
    }

    // ------------------------------------------------------------- matchup

    public record MatchupSummary(
            Integer team1Id,
            String team1Name,
            Integer team2Id,
            String team2Name,
            Integer team1Wins,
            Integer team2Wins,
            Integer ties,
            List<MatchupGame> games) {
    }

    public record MatchupGame(
            Integer season,
            Integer week,
            String seasonType,
            Instant date,
            boolean neutralSite,
            String venue,
            String homeTeam,
            Integer homeScore,
            String awayTeam,
            Integer awayScore,
            String moneyline) {
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
            PickSummary myMoneylinePick,
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
            EspnGameService.EspnGame espn,
            /**
             * Each side's ATS record season by season, newest first. Empty
             * until an admin has loaded ATS for a season the team played.
             */
            List<AtsSummary> homeAtsHistory,
            List<AtsSummary> awayAtsHistory) {
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
     * Returned by create/update/relock so the caller can refresh its card
     * from this response alone - no follow-up GET needed. {@code game}
     * carries both markets' current pick state, not just the one just
     * touched, so the games board can replace the whole row in one shot.
     */
    public record PickResponse(PickSummary pick, GameSummary game) {
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
            String username,
            Selection selection,
            Market market,
            BigDecimal lockedLine,
            PickResult result) {
    }

    /**
     * One market's allowance in the period being viewed.
     *
     * <p>{@code min} is what the period will charge as losses if it closes
     * unmet - see CadenceSettlementService - which is why the board has to be
     * able to say so before it does.
     */
    public record MarketBudget(
            Market market,
            int used,
            /** Null for no minimum. */
            Integer min,
            /** Null for no maximum. */
            Integer max) {
    }

    public record WeekPicks(
            Integer season,
            Integer week,
            /** Picks made in this week, whatever the group's cadence. */
            Integer picksUsed,
            /**
             * Null when there is nothing to count down: the group sets no
             * maximum, or its allowance is per day and a week holds several.
             */
            Integer picksRemaining,
            /** The cap per cadence period. Null means no cap. */
            Integer maxPicks,
            /** Which period {@code maxPicks} applies to, so the UI can say so. */
            Cadence cadence,
            /** The fewest picks the period must close with. 0 means none. */
            int minPicks,
            /** Per-market allowances for the same period, enabled markets only. */
            List<MarketBudget> markets,
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
            /** The unique handle. Shown beside the name, which is not unique. */
            String username,
            /** Every pick in the period, pending ones included. */
            long totalPicks,
            long wins,
            long losses,
            /** Ties. A pick that landed exactly on its number. */
            long pushes,
            /** Scored by the group's own point values. The primary ranking key. */
            double points,
            Double winPercentage,
            /**
             * Losses charged for minimums the member finished a period short
             * of. Included in {@code losses} and {@code points} already; broken
             * out so the board can say why a record looks worse than the picks
             * behind it.
             */
            long penaltyLosses,
            /**
             * Out of an elimination pool - more losses than the group's strikes
             * allow. Always false in a pickem group, which eliminates nobody.
             */
            boolean eliminated) {
    }

    // ------------------------------------------------------------------ misc

    public record CurrentWeek(Integer season, Integer week, List<Integer> availableWeeks,
                              List<Integer> loadedWeeks) {
    }

    /** Options for the games page filters, scoped to the week being viewed. */
    public record GameFilters(List<String> conferences, List<TeamSummary> teams,
                              Double maxSpread) {
    }

    public record MemberProfile(UUID id, String displayName, String username, String email,
                                String role, String theme, String colorMode) {
    }

    // ---------------------------------------------------------------- groups

    /**
     * A group as it appears in a list - my groups, or an admin's list of all of
     * them. Deliberately carries no settings and never the join password.
     */
    public record GroupSummary(
            UUID id,
            String name,
            String description,
            Visibility visibility,
            GroupType groupType,
            Cadence cadence,
            LengthType lengthType,
            Integer startSeason,
            /** Minutes before kickoff that picks close. The footer states it. */
            int lockLeadMinutes,
            /**
             * Which markets this group plays. The board needs them to decide
             * which pick buttons to draw at all - offering one the group has
             * turned off is a button that can only fail.
             */
            boolean moneylineEnabled,
            boolean spreadEnabled,
            boolean totalEnabled,
            /** Who made the group. Null once their account is gone. */
            UUID createdById,
            String creatorName,
            long memberCount,
            /** Whether the caller may edit or delete it - an owner, or an app admin. */
            boolean manageable,
            /** The caller's role in the group, or null if they are not a member. */
            GroupRole myRole,
            /** Pinned to the top of this member's group picker. */
            boolean favorite,
            /**
             * Whether the caller may hand out a link to this group. Public
             * groups are shareable by any member; a private one needs its
             * owner to have opted in.
             */
            boolean shareable,
            Instant createdAt) {
    }

    /**
     * A shared group as its invitee sees it, before signing in.
     *
     * <p>Deliberately thin. Anyone holding the link can read this without an
     * account, so it carries only what a landing page has to say to explain
     * what the invitation is for - never the join password, and never the
     * membership.
     */
    public record ShareInvite(
            UUID groupId,
            String name,
            String description,
            boolean passwordRequired,
            boolean requiresApproval,
            long memberCount,
            /** Who shared it. Null once their account is gone. */
            String sharerName) {
    }

    /**
     * One of a member's groups, as their profile card lists them.
     *
     * <p>Only the groups the viewer also belongs to. A member's card is
     * reachable by anyone signed in, and the leagues someone plays in are not
     * public just because their picks in one of them are.
     */
    public record MemberGroupRow(
            UUID groupId,
            String name,
            GroupType groupType,
            Cadence cadence,
            GroupRole role,
            long memberCount,
            /** Their standing in that group this season, or null if it has no board yet. */
            Integer rank,
            long wins,
            long losses,
            long pushes,
            double points) {
    }

    /** Someone who could be added to a group. Email disambiguates same-named people. */
    public record MemberOption(UUID id, String displayName, String username, String email) {
    }

    /** The token half of a share URL; the frontend builds the rest. */
    public record ShareLinkResponse(String token) {
    }

    /** Where a share link should send the caller, once they are signed in. */
    public record ShareClaim(
            UUID groupId,
            String name,
            /** They are already in, so the link is just a way back to the board. */
            boolean alreadyMember,
            /** Their join is waiting on an owner. */
            boolean pending,
            boolean passwordRequired) {
    }

    /**
     * A public group as a searcher sees it, before joining. {@code
     * passwordRequired} drives the lock icon; the password itself is never
     * serialised.
     */
    public record GroupSearchResult(
            UUID id,
            String name,
            String description,
            boolean passwordRequired,
            long memberCount,
            /** Who made it. Null once their account is gone. */
            String creatorName,
            boolean alreadyMember) {
    }

    /** Full settings, for the group detail page and the edit form. */
    public record GroupDetail(
            UUID id,
            UUID createdById,
            String creatorName,
            long memberCount,
            boolean manageable,
            GroupRole myRole,
            /** Waiting to be approved. Only populated for someone who can act on them. */
            long pendingRequests,
            /**
             * Whether the caller may hand out a link to this group. The page
             * hides the Share button rather than showing one that would be
             * refused.
             */
            boolean shareable,
            Instant createdAt,
            Instant updatedAt,
            /**
             * Everything configurable, in the same shape the update endpoint
             * accepts, so the edit form round-trips without a translation step.
             * The join password is included only for someone who may manage the
             * group - it is how an owner reads it back to share it.
             */
            GroupSettings settings) {
    }

    public record GroupMemberRow(
            UUID userId,
            String displayName,
            String username,
            String email,
            GroupRole role,
            /** Made the group. Independent of role - a creator can be demoted. */
            boolean creator,
            Instant joinedAt) {
    }

    /** Someone waiting for an owner to let them in. */
    public record JoinRequestRow(
            UUID userId,
            String displayName,
            String username,
            String email,
            Instant requestedAt) {
    }

    /**
     * The result of asking to join. {@code pending} means the group requires
     * approval and an owner now has the request; the group detail is withheld
     * until they are actually in.
     */
    public record JoinResult(boolean pending, GroupDetail group) {
    }

    public record JoinGroupRequest(String password) {
    }

    public record AddMemberRequest(@NotNull UUID userId) {
    }

    public record MemberRoleRequest(@NotNull GroupRole role) {
    }

    public record FavoriteRequest(boolean favorite) {
    }
}
