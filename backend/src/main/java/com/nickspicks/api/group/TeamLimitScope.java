package com.nickspicks.api.group;

/**
 * Which markets a team-usage limit counts. Picking a team on the spread and
 * picking them to win are the same bet to some groups and different bets to
 * others, so the scope is configurable rather than assumed.
 */
public enum TeamLimitScope {
    WINNER,
    SPREAD,
    BOTH
}
