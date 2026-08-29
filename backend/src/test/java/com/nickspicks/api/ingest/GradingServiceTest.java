package com.nickspicks.api.ingest;

import com.nickspicks.api.pick.PickResult;
import com.nickspicks.api.pick.Selection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The truth table for grading against the spread.
 *
 * <p>Spreads are from the home team's perspective, matching CFBD: -7.5 means
 * the home team is favored by 7.5.
 */
class GradingServiceTest {

    private final GradingService grading = new GradingService(null);

    @DisplayName("grades a pick against the locked spread")
    @ParameterizedTest(name = "{0} at {1}, {2}-{3} -> {4}")
    @CsvSource({
            // Home favored by 7, home wins by more - covers.
            "HOME, -7.0, 31, 20, WIN",
            // Home favored by 7, home wins by exactly 7 - push.
            "HOME, -7.0, 27, 20, PUSH",
            // Home favored by 7, home wins by less - fails to cover.
            "HOME, -7.0, 24, 20, LOSS",
            // Home favored by 7 and loses outright.
            "HOME, -7.0, 17, 20, LOSS",

            // The away side of the same games.
            "AWAY, -7.0, 31, 20, LOSS",
            "AWAY, -7.0, 27, 20, PUSH",
            "AWAY, -7.0, 24, 20, WIN",
            "AWAY, -7.0, 17, 20, WIN",

            // Home underdog: +3.5 means home can lose by 3 and still cover.
            "HOME, 3.5, 21, 24, WIN",
            "HOME, 3.5, 21, 25, LOSS",
            "AWAY, 3.5, 21, 25, WIN",

            // Half-point lines can never push.
            "HOME, -7.5, 27, 20, LOSS",
            "HOME, -6.5, 27, 20, WIN",

            // Pick'em (line of zero) behaves like a straight-up pick.
            "HOME, 0.0, 21, 20, WIN",
            "HOME, 0.0, 20, 20, PUSH",
            "AWAY, 0.0, 20, 21, WIN",

            // Big favorite blowouts.
            "HOME, -29.5, 56, 7, WIN",
            "AWAY, -29.5, 56, 28, WIN"
    })
    void gradesAgainstTheSpread(Selection selection, BigDecimal spread, int homeScore,
                                int awayScore, PickResult expected) {
        assertThat(grading.grade(selection, spread, homeScore, awayScore)).isEqualTo(expected);
    }

    @DisplayName("grades a total against the combined score")
    @ParameterizedTest(name = "{0} {1}, {2}-{3} -> {4}")
    @CsvSource({
            // 24 + 20 = 44 against a total of 42.5.
            "OVER,  42.5, 24, 20, WIN",
            "UNDER, 42.5, 24, 20, LOSS",

            // The same game against a total of 45.5.
            "OVER,  45.5, 24, 20, LOSS",
            "UNDER, 45.5, 24, 20, WIN",

            // A whole number landing exactly on the total pushes.
            "OVER,  44.0, 24, 20, PUSH",
            "UNDER, 44.0, 24, 20, PUSH",

            // A half point cannot push, however close it lands.
            "OVER,  43.5, 24, 20, WIN",
            "UNDER, 44.5, 24, 20, WIN",

            // A defensive game well under a high total.
            "UNDER, 55.5, 10, 7, WIN",
            "OVER,  55.5, 10, 7, LOSS",

            // A shootout well over a low one.
            "OVER,  38.5, 45, 42, WIN",
            "UNDER, 38.5, 45, 42, LOSS",

            // A shutout still grades - the total is simply the one score.
            "UNDER, 30.5, 21, 0, WIN",
            "OVER,  20.5, 21, 0, WIN"
    })
    void gradesAgainstTheTotal(Selection selection, BigDecimal total, int homeScore,
                               int awayScore, PickResult expected) {
        assertThat(grading.grade(selection, total, homeScore, awayScore)).isEqualTo(expected);
    }

    /**
     * The two markets read the same score differently. A spread pick can win
     * while a total pick on the same game loses, and neither result tells you
     * anything about the other.
     */
    @Test
    void theTwoMarketsGradeIndependently() {
        // Home wins 24-20: covers -3.5, and the 44 points stay under 45.5.
        assertThat(grading.grade(Selection.HOME, new BigDecimal("-3.5"), 24, 20))
                .isEqualTo(PickResult.WIN);
        assertThat(grading.grade(Selection.OVER, new BigDecimal("45.5"), 24, 20))
                .isEqualTo(PickResult.LOSS);
        assertThat(grading.grade(Selection.UNDER, new BigDecimal("45.5"), 24, 20))
                .isEqualTo(PickResult.WIN);
    }
}
