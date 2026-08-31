package com.nickspicks.api.pick;

import com.nickspicks.api.game.Game;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;

import java.time.ZoneId;

/**
 * The label for the period a pick counts against.
 *
 * <p>A group's pick cap is "so many per week" or "so many per day", and the
 * counter row is keyed by whichever it is. This turns a game plus a group into
 * that key, so the rest of the pick path never has to branch on cadence.
 *
 * <p>The period comes from the <em>game</em>, not from the clock. A pick made
 * on Tuesday for Saturday's game counts against Saturday, which is the only
 * reading that makes a cap mean anything.
 */
public final class CadencePeriod {

    /**
     * College football's day boundary. A 10pm Eastern kickoff is Saturday's
     * game to everyone watching it; bucketing on UTC would file it under
     * Sunday and split a single slate across two periods.
     *
     * <p>Public because the games board has to slice days the same way. A
     * board that drew its day boundary anywhere else would show a game that
     * counts against a different day's allowance.
     */
    public static final ZoneId GAME_DAY_ZONE = ZoneId.of("America/New_York");

    private CadencePeriod() {
    }

    /** e.g. {@code 2026-W01} for a weekly group, {@code 2026-09-05} for a daily one. */
    public static String of(Group group, Game game) {
        return of(group.getCadence(), game);
    }

    public static String of(Cadence cadence, Game game) {
        return switch (cadence) {
            case WEEKLY -> weekly(game.getSeason(), game.getWeek());
            case DAILY -> game.getKickoff().atZone(GAME_DAY_ZONE).toLocalDate().toString();
        };
    }

    /**
     * The weekly form, also used by the V14 backfill - keep the two in step if
     * this ever changes.
     */
    public static String weekly(int season, int week) {
        return "%d-W%02d".formatted(season, week);
    }
}
