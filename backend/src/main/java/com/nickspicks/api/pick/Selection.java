package com.nickspicks.api.pick;

/**
 * What a member took.
 *
 * <p>Each selection knows its own market, so the two can never contradict
 * each other in application code - {@code market} is derived here rather than
 * passed in alongside. The database carries the pair as a column plus a check
 * constraint, which is the backstop for anything writing to it directly.
 *
 * <p>The winner market has its own constants rather than reusing HOME and
 * AWAY. Those already mean "this side against the spread", and a constant that
 * meant two different things depending on a market passed beside it would give
 * up the very guarantee this design exists for.
 */
public enum Selection {

    HOME(Market.SPREAD),
    AWAY(Market.SPREAD),
    OVER(Market.TOTAL),
    UNDER(Market.TOTAL),
    HOME_WINNER(Market.WINNER),
    AWAY_WINNER(Market.WINNER);

    private final Market market;

    Selection(Market market) {
        this.market = market;
    }

    public Market market() {
        return market;
    }

    /**
     * Which side of the game this names, or empty for a total.
     *
     * <p>A total is taken against the game rather than against either team, so
     * it has no side - which is also why a per-team pick limit can never apply
     * to one.
     */
    public java.util.Optional<Boolean> isHomeSide() {
        return switch (this) {
            case HOME, HOME_WINNER -> java.util.Optional.of(true);
            case AWAY, AWAY_WINNER -> java.util.Optional.of(false);
            case OVER, UNDER -> java.util.Optional.empty();
        };
    }

    /** The other side of the same market - what an edit switches to. */
    public Selection opposite() {
        return switch (this) {
            case HOME -> AWAY;
            case AWAY -> HOME;
            case OVER -> UNDER;
            case UNDER -> OVER;
            case HOME_WINNER -> AWAY_WINNER;
            case AWAY_WINNER -> HOME_WINNER;
        };
    }
}
