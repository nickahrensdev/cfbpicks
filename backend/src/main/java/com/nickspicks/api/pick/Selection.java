package com.nickspicks.api.pick;

/**
 * What a member took.
 *
 * <p>Each selection knows its own market, so the two can never contradict
 * each other in application code - {@code market} is derived here rather than
 * passed in alongside. The database carries the pair as a column plus a check
 * constraint, which is the backstop for anything writing to it directly.
 */
public enum Selection {

    HOME(Market.SPREAD),
    AWAY(Market.SPREAD),
    OVER(Market.TOTAL),
    UNDER(Market.TOTAL);

    private final Market market;

    Selection(Market market) {
        this.market = market;
    }

    public Market market() {
        return market;
    }

    /** The other side of the same market - what an edit switches to. */
    public Selection opposite() {
        return switch (this) {
            case HOME -> AWAY;
            case AWAY -> HOME;
            case OVER -> UNDER;
            case UNDER -> OVER;
        };
    }
}
