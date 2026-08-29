package com.nickspicks.api.ranking;

import java.util.List;

/**
 * The polls this site stores, in the order used to pick the single rank shown
 * beside a team's name.
 *
 * <p>The committee only publishes from roughly week 11, so earlier in the
 * season the AP poll wins, and the coaches poll is the last resort. Choosing
 * one poll per week keeps a team from appearing as #3 in one place and #5 in
 * another on the same screen.
 */
public enum Poll {

    PLAYOFF_COMMITTEE("Playoff Committee Rankings"),
    AP_TOP_25("AP Top 25"),
    COACHES("Coaches Poll");

    private final String cfbdName;

    Poll(String cfbdName) {
        this.cfbdName = cfbdName;
    }

    /** The poll's name exactly as the data provider spells it. */
    public String cfbdName() {
        return cfbdName;
    }

    /** Highest priority first. */
    public static List<Poll> priorityOrder() {
        return List.of(PLAYOFF_COMMITTEE, AP_TOP_25, COACHES);
    }

    public static List<String> cfbdNames() {
        return priorityOrder().stream().map(Poll::cfbdName).toList();
    }

    public static Poll fromCfbdName(String name) {
        return priorityOrder().stream()
                .filter(poll -> poll.cfbdName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
