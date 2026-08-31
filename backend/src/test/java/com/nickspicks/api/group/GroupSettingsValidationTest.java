package com.nickspicks.api.group;

import com.nickspicks.api.group.GroupExceptions.InvalidGroupSettingsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cross-field settings rules, as a truth table. These are the combinations
 * Bean Validation cannot express, so they are the ones that would otherwise
 * reach Postgres and come back as an unreadable constraint violation.
 */
class GroupSettingsValidationTest {

    @Test
    void acceptsAStraightforwardPickemGroup() {
        assertThatCode(() -> pickem().build().validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsAnEliminationGroupThatResetsEachYear() {
        assertThatCode(() -> elimination().build().validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsAGroupWithNoPickOptions() {
        assertThatThrownBy(() -> pickem().winner(false).spread(false).total(false).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("at least one pick option");
    }

    @Test
    void acceptsAnySinglePickOption() {
        assertThatCode(() -> pickem().winner(true).spread(false).total(false).build().validate())
                .doesNotThrowAnyException();
        assertThatCode(() -> pickem().winner(false).spread(true).total(false).build().validate())
                .doesNotThrowAnyException();
        assertThatCode(() -> pickem().winner(false).spread(false).total(true).build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAMinimumAboveTheMaximum() {
        assertThatThrownBy(() -> elimination().min(5).max(3).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("cannot be more than the maximum");
    }

    @Test
    void allowsAMinimumWhenThereIsNoMaximum() {
        assertThatCode(() -> elimination().min(5).max(null).build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAMinimumEqualToTheMaximum() {
        assertThatCode(() -> elimination().min(3).max(3).build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsATeamLimitWithoutAScope() {
        assertThatThrownBy(() -> pickem().teamLimit(2, null).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("needs a scope");
    }

    @Test
    void rejectsAScopeWithoutATeamLimit() {
        assertThatThrownBy(() -> pickem().teamLimit(null, TeamLimitScope.BOTH).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("needs a limit");
    }

    @Test
    void acceptsATeamLimitWithItsScope() {
        assertThatCode(() -> pickem().teamLimit(2, TeamLimitScope.SPREAD).build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAContinuousEliminationGroup() {
        assertThatThrownBy(() -> elimination().length(LengthType.CONTINUOUS).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("reset each year");
    }

    @Test
    void rejectsAnEliminationGroupWithNoStrikeCount() {
        assertThatThrownBy(() -> elimination().strikes(null).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("how many wrong picks");
    }

    @Test
    void ignoresTheStrikeCountOnAPickemGroup() {
        // Pickem never eliminates anyone, so a missing strike count is not a
        // problem - the entity drops the value rather than the validator
        // demanding one.
        assertThatCode(() -> pickem().strikes(null).build().validate()).doesNotThrowAnyException();
    }

    @Test
    void allowsAContinuousPickemGroup() {
        assertThatCode(() -> pickem().length(LengthType.CONTINUOUS).build().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAMarketWhoseMinimumIsAboveItsOwnMaximum() {
        assertThatThrownBy(() -> pickem().spreadRange(4, 2).build().validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("minimum spread per week");
    }

    @Test
    void rejectsPerMarketMinimumsThatDoNotFitInsideTheOverallAllowance() {
        // 3 spreads and 3 winners is six picks a week, in a group that allows
        // five. Every member would end every week in breach no matter what
        // they picked, so the settings are refused rather than the penalty
        // handed out later.
        assertThatThrownBy(() -> pickem()
                .max(5)
                .spreadRange(3, null)
                .winnerRange(3, null)
                .build()
                .validate())
                .isInstanceOf(InvalidGroupSettingsException.class)
                .hasMessageContaining("no one could satisfy them all");
    }

    @Test
    void ignoresAMinimumOnAMarketTheGroupDoesNotPlay() {
        // The entity drops limits on a disabled market, so they cannot make an
        // otherwise valid group unsatisfiable.
        assertThatCode(() -> pickem()
                .max(3)
                .winner(false)
                .winnerRange(10, null)
                .build()
                .validate())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPerMarketMinimumsThatExactlyFillTheAllowance() {
        assertThatCode(() -> pickem()
                .max(5)
                .spreadRange(3, null)
                .totalRange(2, null)
                .winner(false)
                .build()
                .validate())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPerMarketLimitsWhenTheGroupSetsNoOverallMaximum() {
        assertThatCode(() -> pickem().max(null).spreadRange(3, 8).build().validate())
                .doesNotThrowAnyException();
    }

    /** A maximum of zero is a real setting - that market is off in all but name. */
    @Test
    void acceptsAMaximumOfZero() {
        assertThatCode(() -> pickem().winnerRange(null, 0).build().validate())
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ setup

    private static Builder pickem() {
        return new Builder().type(GroupType.PICKEM).length(LengthType.CONTINUOUS).min(0).strikes(null);
    }

    private static Builder elimination() {
        return new Builder().type(GroupType.ELIMINATION).length(LengthType.PER_YEAR).min(1).strikes(2);
    }

    /** Keeps each test to the one field it is about. */
    private static final class Builder {

        private GroupType type = GroupType.PICKEM;
        private LengthType length = LengthType.CONTINUOUS;
        private boolean winner = true;
        private boolean spread = true;
        private boolean total = true;
        private Integer max;
        private int min;
        private Integer strikes;
        private Integer teamLimit;
        private TeamLimitScope scope;
        private Integer winnerMin;
        private Integer winnerMax;
        private Integer spreadMin;
        private Integer spreadMax;
        private Integer totalMin;
        private Integer totalMax;

        Builder type(GroupType value) {
            this.type = value;
            return this;
        }

        Builder length(LengthType value) {
            this.length = value;
            return this;
        }

        Builder winner(boolean value) {
            this.winner = value;
            return this;
        }

        Builder spread(boolean value) {
            this.spread = value;
            return this;
        }

        Builder total(boolean value) {
            this.total = value;
            return this;
        }

        Builder max(Integer value) {
            this.max = value;
            return this;
        }

        Builder min(int value) {
            this.min = value;
            return this;
        }

        Builder strikes(Integer value) {
            this.strikes = value;
            return this;
        }

        Builder winnerRange(Integer minimum, Integer maximum) {
            this.winnerMin = minimum;
            this.winnerMax = maximum;
            return this;
        }

        Builder spreadRange(Integer minimum, Integer maximum) {
            this.spreadMin = minimum;
            this.spreadMax = maximum;
            return this;
        }

        Builder totalRange(Integer minimum, Integer maximum) {
            this.totalMin = minimum;
            this.totalMax = maximum;
            return this;
        }

        Builder teamLimit(Integer limit, TeamLimitScope value) {
            this.teamLimit = limit;
            this.scope = value;
            return this;
        }

        GroupSettings build() {
            BigDecimal one = BigDecimal.ONE;
            BigDecimal zero = BigDecimal.ZERO;
            BigDecimal half = new BigDecimal("0.5");

            return new GroupSettings("The Office", "A league", Visibility.PUBLIC, null,
                    type, Cadence.WEEKLY, length, 2026,
                    30, max, min, true, false, false,
                    winner, spread, total,
                    winnerMin, winnerMax, spreadMin, spreadMax, totalMin, totalMax,
                    one, zero, half, one, zero, half, one, zero, half,
                    strikes, teamLimit, scope);
        }
    }
}
