package com.nickspicks.api.pick;

/**
 * VOID is for picks on games that were canceled - they are excluded from the
 * standings entirely rather than counted as a loss.
 */
public enum PickResult {
    PENDING,
    WIN,
    LOSS,
    PUSH,
    VOID
}
