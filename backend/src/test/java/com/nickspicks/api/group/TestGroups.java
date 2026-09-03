package com.nickspicks.api.group;

import java.math.BigDecimal;

/**
 * Group fixtures for tests that need a league to play in but are not about
 * group settings themselves.
 *
 * <p>The defaults reproduce how the site behaved before groups existed - a
 * weekly pick'em, ten picks, both markets, win 1 / push 0.5 / loss 0 - so a
 * test written against the old rules still reads the same way.
 */
public final class TestGroups {

    private TestGroups() {
    }

    public static GroupSettings weeklyPickem() {
        return settings("Test League", Cadence.WEEKLY, 10);
    }

    public static GroupSettings settings(String name, Cadence cadence, Integer maxPicks) {
        return settings(name, cadence, maxPicks, true, true);
    }

    /** For tests about which markets a group plays. */
    public static GroupSettings settings(String name, Cadence cadence, Integer maxPicks,
                                         boolean spread, boolean total) {
        return settings(name, cadence, maxPicks, spread, total, false);
    }

    public static GroupSettings settings(String name, Cadence cadence, Integer maxPicks,
                                         boolean spread, boolean total, boolean moneyline) {
        return withMarketLimits(name, cadence, maxPicks, spread, total, moneyline,
                null, null, null, null, null, null);
    }

    /**
     * The same, with per-market caps. Nulls mean no limit, so a test only has
     * to name the one market it is actually about.
     */
    public static GroupSettings withMarketLimits(
            String name, Cadence cadence, Integer maxPicks,
            boolean spread, boolean total, boolean moneyline,
            Integer moneylineMin, Integer moneylineMax,
            Integer spreadMin, Integer spreadMax,
            Integer totalMin, Integer totalMax) {

        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal half = new BigDecimal("0.5");

        return new GroupSettings(
                name, null, Visibility.PUBLIC, null,
                GroupType.PICKEM, cadence, LengthType.CONTINUOUS, 2026,
                30, maxPicks, 0, true, false, false,
                moneyline, spread, total,
                moneylineMin, moneylineMax, spreadMin, spreadMax, totalMin, totalMax,
                one, zero, half,
                one, zero, half,
                one, zero, half,
                null, null, null,
                java.time.LocalDate.of(2000, 1, 1), false);
    }

    /** Scoring where a spread win is worth more than a total win. */
    public static GroupSettings weighted(String name, BigDecimal spreadWin, BigDecimal totalWin,
                                         BigDecimal loss, BigDecimal push) {
        return new GroupSettings(
                name, null, Visibility.PUBLIC, null,
                GroupType.PICKEM, Cadence.WEEKLY, LengthType.CONTINUOUS, 2026,
                30, 10, 0, true, false, false,
                false, true, true,
                null, null, null, null, null, null,
                BigDecimal.ONE, loss, push,
                spreadWin, loss, push,
                totalWin, loss, push,
                null, null, null,
                java.time.LocalDate.of(2000, 1, 1), false);
    }
}
