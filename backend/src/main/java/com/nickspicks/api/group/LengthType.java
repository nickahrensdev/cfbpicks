package com.nickspicks.api.group;

/**
 * How far a leaderboard reaches back.
 *
 * <p>{@code CONTINUOUS} is one all-time board that keeps accumulating season
 * after season. {@code PER_YEAR} gives each season its own board, with prior
 * seasons still readable. Either way the group itself lives on - neither
 * setting ends it.
 */
public enum LengthType {
    CONTINUOUS,
    PER_YEAR
}
