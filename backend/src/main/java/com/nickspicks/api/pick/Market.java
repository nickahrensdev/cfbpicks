package com.nickspicks.api.pick;

/**
 * What a pick is played against.
 *
 * <p>All three grade to the same WIN/LOSS/PUSH outcomes and draw on the same
 * allowance, so a member spends their picks across whichever mix they like and
 * everything downstream - standings, the leaderboard, the audit - is
 * indifferent to which was played. What each is worth is the group's decision.
 */
public enum Market {

    /** A side against the point spread. */
    SPREAD,

    /** Over or under the combined score. */
    TOTAL,

    /**
     * A side to win the game outright.
     *
     * <p>The only market with no number: nothing is locked, nothing can move,
     * and there is no better line to take later. A game is moneyline-pickable as
     * soon as it is scheduled, whether or not a bookmaker has posted anything.
     */
    MONEYLINE
}
