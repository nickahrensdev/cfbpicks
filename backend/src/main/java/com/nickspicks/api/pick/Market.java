package com.nickspicks.api.pick;

/**
 * What a pick is played against.
 *
 * <p>Both markets grade to the same WIN/LOSS/PUSH outcomes and draw on the
 * same weekly allowance, so a member spends their ten picks across whichever
 * mix they like.
 */
public enum Market {

    /** A side against the point spread. */
    SPREAD,

    /** Over or under the combined score. */
    TOTAL
}
