package com.nickspicks.api.group;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Everything a group can be configured with, as one payload shared by create
 * and update.
 *
 * <p>Per-field rules are Bean Validation annotations, which the existing
 * handler turns into a {@code VALIDATION_FAILED} response with a per-field
 * error map the UI already knows how to render. The rules that span fields
 * cannot be expressed that way and live in {@link #validate()} instead, where
 * they can say something useful about which combination is wrong. Postgres
 * enforces the same set again as check constraints.
 */
public record GroupSettings(

        @NotBlank @Size(max = 60) String name,
        @Size(max = 500) String description,
        @NotNull Visibility visibility,
        @Size(max = 60) String joinPassword,

        @NotNull GroupType groupType,
        @NotNull Cadence cadence,
        @NotNull LengthType lengthType,
        @NotNull @Min(2000) Integer startSeason,

        @Min(0) int lockLeadMinutes,
        @Min(1) Integer maxPicksPerCadence,
        @Min(0) int minPicksPerCadence,
        boolean multiplePicksPerGame,
        /** Joining produces a request an owner has to approve. */
        boolean requireApproval,
        /**
         * Lets ordinary members share a private group. Ignored for a public
         * one, which any member may share regardless.
         */
        boolean shareableByMembers,

        boolean moneylineEnabled,
        boolean spreadEnabled,
        boolean totalEnabled,

        /**
         * Per-market limits per period. Null means no limit; 0 is a real
         * maximum, so these are bounded below by 0 rather than 1.
         */
        @Min(0) Integer moneylineMinPerCadence,
        @Min(0) Integer moneylineMaxPerCadence,
        @Min(0) Integer spreadMinPerCadence,
        @Min(0) Integer spreadMaxPerCadence,
        @Min(0) Integer totalMinPerCadence,
        @Min(0) Integer totalMaxPerCadence,

        @NotNull BigDecimal moneylineWinPoints,
        @NotNull BigDecimal moneylineLossPoints,
        @NotNull BigDecimal moneylinePushPoints,
        @NotNull BigDecimal spreadWinPoints,
        @NotNull BigDecimal spreadLossPoints,
        @NotNull BigDecimal spreadPushPoints,
        @NotNull BigDecimal totalWinPoints,
        @NotNull BigDecimal totalLossPoints,
        @NotNull BigDecimal totalPushPoints,

        @Min(0) Integer strikesAllowed,
        @Min(1) Integer teamPickLimit,
        TeamLimitScope teamPickLimitScope) {

    /**
     * Rejects combinations that are individually valid but nonsensical
     * together. Called by the service before anything is written.
     */
    public void validate() {
        if (!moneylineEnabled && !spreadEnabled && !totalEnabled) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    "Turn on at least one pick option - a group with none has nothing to pick");
        }

        if (maxPicksPerCadence != null && minPicksPerCadence > maxPicksPerCadence) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    "The minimum picks per %s cannot be more than the maximum"
                            .formatted(cadence == Cadence.DAILY ? "day" : "week"));
        }

        requireMarketRange("moneyline", "moneylines", moneylineMinPerCadence, moneylineMaxPerCadence);
        requireMarketRange("spread", "spreads", spreadMinPerCadence, spreadMaxPerCadence);
        requireMarketRange("over/under", "over/unders", totalMinPerCadence, totalMaxPerCadence);
        requireMarketMinimumsFit();

        if ((teamPickLimit == null) != (teamPickLimitScope == null)) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    "A team pick limit needs a scope, and a scope needs a limit");
        }

        if (groupType == GroupType.ELIMINATION) {
            if (lengthType != LengthType.PER_YEAR) {
                throw new GroupExceptions.InvalidGroupSettingsException(
                        "An elimination group has to reset each year - once everyone is out "
                                + "a continuous pool has no way to start over");
            }
            if (strikesAllowed == null) {
                throw new GroupExceptions.InvalidGroupSettingsException(
                        "An elimination group needs to say how many wrong picks eliminate a member");
            }
        }
    }

    /** A market whose minimum is above its own maximum can never be satisfied. */
    private void requireMarketRange(String market, String plural, Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    "The minimum %s per %s cannot be more than the maximum - nobody could pick %s"
                            .formatted(market, periodNoun(), plural));
        }
    }

    /**
     * The per-market minimums have to fit inside the overall allowance.
     *
     * <p>Requiring 3 spreads and 3 moneylines in a period that allows 5 picks
     * total is not a hard rule, it is an impossible one: every member would end
     * every period in breach however they picked. Better to refuse the settings
     * than to hand out an unavoidable penalty later.
     */
    private void requireMarketMinimumsFit() {
        if (maxPicksPerCadence == null) {
            return;
        }
        int required = required(moneylineEnabled, moneylineMinPerCadence)
                + required(spreadEnabled, spreadMinPerCadence)
                + required(totalEnabled, totalMinPerCadence);

        if (required > maxPicksPerCadence) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    ("The per-market minimums add up to %d picks per %s, but the group only "
                            + "allows %d - no one could satisfy them all")
                            .formatted(required, periodNoun(), maxPicksPerCadence));
        }
    }

    private int required(boolean enabled, Integer min) {
        return enabled && min != null ? min : 0;
    }

    private String periodNoun() {
        return cadence == Cadence.DAILY ? "day" : "week";
    }
}
