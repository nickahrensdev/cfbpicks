package com.nickspicks.api.group;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The settings every personal group is created with, and keeps.
 *
 * <p>A personal group is a private board of one. It exists because every board
 * in this app is a group's board, so a member who has joined no league has
 * nothing to pick at all - the games page shows an invitation to find a group
 * where the schedule should be. That is the emptiest possible first run, and
 * it lands on exactly the people still deciding whether they want the app.
 *
 * <p>It is an ordinary group in every way the pick path can see: one member,
 * who is also its owner. Nothing in {@code PickService}, grading or the
 * leaderboard needs a special case. What makes it personal is refusal, and
 * that lives in {@link GroupService} - it cannot be joined, shared, edited or
 * deleted by anyone, its owner and app admins included.
 *
 * <p>Being uneditable is why these values are constants rather than a
 * template someone starts from. Changing them here changes what <em>new</em>
 * boards are created with; boards that already exist keep what they were made
 * with, and would need a migration to move.
 */
public final class PersonalGroups {

    public static final String NAME = "My Board";
    public static final String DESCRIPTION = "Your own board. Nobody else can join it.";

    /**
     * Five minutes, against the 30 an ordinary group defaults to.
     *
     * <p>Nobody else can see these picks before kickoff, so the long lead that
     * stops a league being scouted buys nothing here - it would only take
     * games away from someone picking alone.
     */
    public static final int LOCK_LEAD_MINUTES = 5;

    private PersonalGroups() {
    }

    /**
     * The board a new account gets.
     *
     * <p>No limits of any kind: no cap, no floor, no per-market range, no team
     * limit. There is no one to keep it fair against, and a rule whose only
     * effect is to refuse your own picks is just an obstacle.
     *
     * <p><b>Scoring.</b> The spread and the total are priced as coin flips,
     * because that is what the line makes them: 1 for a win, 0 for a loss, a
     * half back on a push. The moneyline is not a coin flip - a heavy
     * favourite wins outright far more often than it covers - so pricing it
     * the same would make "take every favourite's moneyline" strictly the best
     * thing to do, and with no pick limit here the score would become a count
     * of how many games you clicked.
     *
     * <p>So a moneyline pays half and costs half. Per pick that is worth:
     *
     * <pre>
     *   spread or total at ~50%      0.5 x 1                = 0.50
     *   moneyline on an 80% favourite 0.8 x 0.5 - 0.2 x 0.5 = 0.30
     *   moneyline at 65%                                    = 0.15
     *   moneyline on a coin flip                            = 0.00
     * </pre>
     *
     * <p>Moneylines stay worth taking when you are confident, and never beat
     * simply picking spreads well. The negative is what does the work: without
     * it a moneyline would be free upside and volume would win.
     */
    public static GroupSettings settings() {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal half = new BigDecimal("0.5");
        BigDecimal minusHalf = new BigDecimal("-0.5");

        return new GroupSettings(
                NAME,
                DESCRIPTION,
                // Private and unshareable. The refusals in GroupService are
                // what actually enforce that; this keeps it out of search even
                // if one of them were ever missed.
                Visibility.PRIVATE,
                null,

                GroupType.PICKEM,
                Cadence.WEEKLY,
                // Continuous: a board of one is a running personal record, not
                // a season that needs resetting to keep a race interesting.
                LengthType.CONTINUOUS,
                LocalDate.now().getYear(),

                LOCK_LEAD_MINUTES,
                null,   // no maximum picks per week
                0,      // and no minimum, so nothing is ever charged as a loss
                true,   // spread and total on the same game is fine
                false,  // no approvals - nobody can ask to join
                false,  // nothing to share

                true, true, true,

                // No per-market ranges either.
                null, null, null, null, null, null,

                half, minusHalf, zero,   // moneyline: win / loss / push
                one, zero, half,         // spread
                one, zero, half,         // total

                null,   // strikes are an elimination idea
                null, null);
    }
}
