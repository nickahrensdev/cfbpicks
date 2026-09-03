package com.nickspicks.api.pick;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.game.GameStatus;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupMember;
import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupRepository;
import com.nickspicks.api.group.GroupRole;
import com.nickspicks.api.group.GroupSettings;
import com.nickspicks.api.group.TeamLimitScope;
import com.nickspicks.api.group.TestGroups;
import com.nickspicks.api.pick.PickExceptions.InvalidPickException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The limits a pick can be judged against the moment it is made: per-market
 * maximums, the per-team limit, and one-pick-per-game.
 *
 * <p>Each of these exists because the overall cap cannot express it. A group
 * with three markets, one scoring table and only a total cap has a dominant
 * strategy - take heavy favourites to win and never touch a spread - and these
 * are the settings that take it away.
 */
class PickLimitsIntegrationTest extends IntegrationTest {

    @Autowired
    private PickService picks;

    @Autowired
    private PickRepository pickRepository;

    @Autowired
    private CadenceEntryRepository entries;

    @Autowired
    private PickAuditRepository audits;

    @Autowired
    private GameRepository games;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    @Override
    protected void cleanUp() {
        audits.deleteAll();
        pickRepository.deleteAll();
        entries.deleteAll();
        groupMembers.deleteAll();
        groups.deleteAll();
        games.deleteAll();
        users.deleteAll();
    }

    // ---------------------------------------------------------- start date

    /**
     * A group that starts next week does not play this week's games.
     *
     * <p>The counterpart to settlement ignoring periods before the start date:
     * without this, those games could be picked and scored but would never be
     * counted toward a minimum - and in an elimination pool would contribute
     * losses from before the pool existed.
     */
    @Test
    void refusesPicksOnGamesBeforeTheGroupStarts() {
        Group group = group(TestGroups.settings("Starts later", Cadence.WEEKLY, 10,
                true, true, true));
        // The shared fixture starts in 2000; move this one into the future.
        group.apply(startingOn(group, LocalDate.now(CadencePeriod.GAME_DAY_ZONE).plusDays(7)));
        group = groups.save(group);
        UUID member = member(group, "early");

        Game tomorrow = game(500L, 1, Instant.now().plus(1, ChronoUnit.DAYS));

        Group saved = group;
        assertThatThrownBy(() -> picks.create(saved, member, tomorrow.getId(), Selection.HOME))
                .isInstanceOf(InvalidPickException.class)
                .hasMessageContaining("games before then are not part of it");
    }

    @Test
    void allowsPicksOnTheStartDayItself() {
        Instant kickoff = Instant.now().plus(2, ChronoUnit.DAYS);
        LocalDate startDay = kickoff.atZone(CadencePeriod.GAME_DAY_ZONE).toLocalDate();

        Group group = group(TestGroups.settings("Starts that day", Cadence.WEEKLY, 10,
                true, true, true));
        group.apply(startingOn(group, startDay));
        group = groups.save(group);
        UUID member = member(group, "onthed");

        Game opener = game(501L, 1, kickoff);

        Group saved = group;
        assertThatCode(() -> picks.create(saved, member, opener.getId(), Selection.HOME))
                .doesNotThrowAnyException();
    }

    /** The group's own settings with a different start date. */
    private GroupSettings startingOn(Group group, LocalDate startsOn) {
        GroupSettings base = TestGroups.settings(group.getName(), group.getCadence(), 10,
                true, true, true);
        return new GroupSettings(
                base.name(), base.description(), base.visibility(), base.joinPassword(),
                base.groupType(), base.cadence(), base.lengthType(), base.startSeason(),
                base.lockLeadMinutes(), base.maxPicksPerCadence(), base.minPicksPerCadence(),
                base.multiplePicksPerGame(), base.requireApproval(), base.shareableByMembers(),
                base.moneylineEnabled(), base.spreadEnabled(), base.totalEnabled(),
                null, null, null, null, null, null,
                base.moneylineWinPoints(), base.moneylineLossPoints(), base.moneylinePushPoints(),
                base.spreadWinPoints(), base.spreadLossPoints(), base.spreadPushPoints(),
                base.totalWinPoints(), base.totalLossPoints(), base.totalPushPoints(),
                base.strikesAllowed(), base.teamPickLimit(), base.teamPickLimitScope(),
                startsOn, false);
    }

    // ------------------------------------------------------- per-market caps

    @Test
    void refusesMorePicksInAMarketThanThatMarketAllows() {
        Group group = group(TestGroups.withMarketLimits(
                "Capped", Cadence.WEEKLY, 10, true, true, true,
                null, 2, null, null, null, null));
        UUID member = member(group, "capped");

        picks.create(group, member, game(1L).getId(), Selection.HOME_ML);
        picks.create(group, member, game(2L).getId(), Selection.AWAY_ML);

        assertThatThrownBy(() ->
                picks.create(group, member, game(3L).getId(), Selection.HOME_ML))
                .isInstanceOf(WeeklyLimitReachedException.class)
                .hasMessageContaining("2 moneyline picks for this week");

        // The overall allowance is untouched - the member can still pick, just
        // not in the market they have used up. That is the entire point.
        assertThatCode(() -> picks.create(group, member, game(3L).getId(), Selection.HOME))
                .doesNotThrowAnyException();
    }

    @Test
    void countsEachMarketAgainstItsOwnCap() {
        Group group = group(TestGroups.withMarketLimits(
                "Split", Cadence.WEEKLY, 10, true, true, true,
                null, 1, null, 1, null, 1));
        UUID member = member(group, "split");

        // One game, all three markets - each fills a different allowance.
        Game game = game(1L);
        picks.create(group, member, game.getId(), Selection.HOME_ML);
        picks.create(group, member, game.getId(), Selection.HOME);
        picks.create(group, member, game.getId(), Selection.OVER);

        assertThatThrownBy(() -> picks.create(group, member, game(2L).getId(), Selection.OVER))
                .isInstanceOf(WeeklyLimitReachedException.class);
        assertThatThrownBy(() -> picks.create(group, member, game(2L).getId(), Selection.AWAY))
                .isInstanceOf(WeeklyLimitReachedException.class);
    }

    /**
     * The cap is per period, not per season - the week rolling over restores
     * the allowance, which is what "per cadence" has to mean.
     */
    @Test
    void aNewWeekRestoresTheMarketAllowance() {
        Group group = group(TestGroups.withMarketLimits(
                "Weekly", Cadence.WEEKLY, 10, true, true, true,
                null, 1, null, null, null, null));
        UUID member = member(group, "weekly");

        picks.create(group, member, game(1L, 1).getId(), Selection.HOME_ML);

        assertThatThrownBy(() ->
                picks.create(group, member, game(2L, 1).getId(), Selection.HOME_ML))
                .isInstanceOf(WeeklyLimitReachedException.class);

        assertThatCode(() ->
                picks.create(group, member, game(3L, 2).getId(), Selection.HOME_ML))
                .doesNotThrowAnyException();
    }

    /**
     * A daily group's period is a day, and its counting has to follow. This is
     * the case a week-shaped query gets wrong: two games in the same week but
     * different days both fit, where two on the same day do not.
     */
    @Test
    void aDailyGroupCountsTheMarketCapPerDay() {
        Group group = group(TestGroups.withMarketLimits(
                "Daily", Cadence.DAILY, 5, true, true, true,
                null, 1, null, null, null, null));
        UUID member = member(group, "daily");

        // Both in week 1; the second is a day later.
        //
        // Anchored to midday in the zone CadencePeriod buckets by, not to the
        // clock. Instant.now() plus two hours lands on the *next* game day
        // whenever the suite runs late in the evening Eastern, which made this
        // assertion fail for a couple of hours every night and pass the rest
        // of the time. Midday leaves room for the +2h in either direction.
        Instant saturday = LocalDate.now(CadencePeriod.GAME_DAY_ZONE)
                .plusDays(3)
                .atTime(12, 0)
                .atZone(CadencePeriod.GAME_DAY_ZONE)
                .toInstant();
        Game first = game(1L, 1, saturday);
        Game sameDay = game(2L, 1, saturday.plus(2, ChronoUnit.HOURS));
        Game nextDay = game(3L, 1, saturday.plus(1, ChronoUnit.DAYS));

        picks.create(group, member, first.getId(), Selection.HOME_ML);

        assertThatThrownBy(() ->
                picks.create(group, member, sameDay.getId(), Selection.AWAY_ML))
                .isInstanceOf(WeeklyLimitReachedException.class);

        assertThatCode(() ->
                picks.create(group, member, nextDay.getId(), Selection.HOME_ML))
                .doesNotThrowAnyException();
    }

    @Test
    void aMaximumOfZeroClosesTheMarketOutright() {
        Group group = group(TestGroups.withMarketLimits(
                "No moneylines", Cadence.WEEKLY, 10, true, true, true,
                null, 0, null, null, null, null));
        UUID member = member(group, "nomoneylines");

        assertThatThrownBy(() ->
                picks.create(group, member, game(1L).getId(), Selection.HOME_ML))
                .isInstanceOf(WeeklyLimitReachedException.class)
                .hasMessageContaining("does not allow moneyline picks");
    }

    // ------------------------------------------------------- team pick limit

    @Test
    void refusesATeamThatHasAlreadyBeenPickedItsLimitOfTimes() {
        Group group = group(teamLimited(2, TeamLimitScope.BOTH));
        UUID member = member(group, "loyal");

        // "Home 1" is the home side of all three games, in different weeks.
        picks.create(group, member, sameHomeTeam(1L, 1).getId(), Selection.HOME);
        picks.create(group, member, sameHomeTeam(2L, 2).getId(), Selection.HOME);

        assertThatThrownBy(() ->
                picks.create(group, member, sameHomeTeam(3L, 3).getId(), Selection.HOME))
                .isInstanceOf(InvalidPickException.class)
                .hasMessageContaining("already picked Anytown State 2 times");
    }

    /** The limit is on the team, not on the side of the game it appears on. */
    @Test
    void countsATeamWhicheverSideOfTheGameItIsOn() {
        Group group = group(teamLimited(1, TeamLimitScope.BOTH));
        UUID member = member(group, "either");

        picks.create(group, member, sameHomeTeam(1L, 1).getId(), Selection.HOME);

        Game away = game(2L, 2);
        away.setAwayTeam("Anytown State");
        games.save(away);

        assertThatThrownBy(() -> picks.create(group, member, away.getId(), Selection.AWAY))
                .isInstanceOf(InvalidPickException.class);
    }

    /** A scope of SPREAD leaves moneyline picks on that team alone, and vice versa. */
    @Test
    void onlyCountsMarketsTheScopeNames() {
        Group group = group(teamLimited(1, TeamLimitScope.SPREAD));
        UUID member = member(group, "scoped");

        picks.create(group, member, sameHomeTeam(1L, 1).getId(), Selection.HOME);

        // Same team, but a moneyline pick - out of scope, so it is allowed.
        assertThatCode(() ->
                picks.create(group, member, sameHomeTeam(2L, 2).getId(), Selection.HOME_ML))
                .doesNotThrowAnyException();

        assertThatThrownBy(() ->
                picks.create(group, member, sameHomeTeam(3L, 3).getId(), Selection.HOME))
                .isInstanceOf(InvalidPickException.class);
    }

    /** A total names no team, so it can never use one up. */
    @Test
    void neverCountsTotalsAgainstATeamLimit() {
        Group group = group(teamLimited(1, TeamLimitScope.BOTH));
        UUID member = member(group, "totals");

        picks.create(group, member, sameHomeTeam(1L, 1).getId(), Selection.OVER);

        assertThatCode(() ->
                picks.create(group, member, sameHomeTeam(2L, 2).getId(), Selection.HOME))
                .doesNotThrowAnyException();
    }

    // --------------------------------------------------- one pick per game

    @Test
    void refusesASecondMarketOnTheSameGameWhenTheGroupAllowsOnlyOne() {
        Group group = group(singlePickPerGame());
        UUID member = member(group, "single");
        Game game = game(1L);

        picks.create(group, member, game.getId(), Selection.HOME);

        assertThatThrownBy(() -> picks.create(group, member, game.getId(), Selection.OVER))
                .isInstanceOf(InvalidPickException.class)
                .hasMessageContaining("one pick per game");

        // A different game is still fine - the rule is about this game only.
        assertThatCode(() -> picks.create(group, member, game(2L).getId(), Selection.OVER))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsSeveralMarketsOnOneGameByDefault() {
        Group group = group(TestGroups.settings("Open", Cadence.WEEKLY, 10, true, true, true));
        UUID member = member(group, "many");
        Game game = game(1L);

        picks.create(group, member, game.getId(), Selection.HOME);
        picks.create(group, member, game.getId(), Selection.OVER);
        picks.create(group, member, game.getId(), Selection.HOME_ML);

        assertThat(pickRepository.findAllByGroupIdAndUserIdAndGameId(
                group.getId(), member, game.getId())).hasSize(3);
    }

    // ------------------------------------------------------------- fixtures

    private GroupSettings teamLimited(int limit, TeamLimitScope scope) {
        GroupSettings base = TestGroups.settings("Team limited", Cadence.WEEKLY, 20,
                true, true, true);
        return new GroupSettings(
                base.name(), base.description(), base.visibility(), base.joinPassword(),
                base.groupType(), base.cadence(), base.lengthType(), base.startSeason(),
                base.lockLeadMinutes(), base.maxPicksPerCadence(), base.minPicksPerCadence(),
                base.multiplePicksPerGame(), base.requireApproval(), base.shareableByMembers(),
                base.moneylineEnabled(), base.spreadEnabled(), base.totalEnabled(),
                null, null, null, null, null, null,
                base.moneylineWinPoints(), base.moneylineLossPoints(), base.moneylinePushPoints(),
                base.spreadWinPoints(), base.spreadLossPoints(), base.spreadPushPoints(),
                base.totalWinPoints(), base.totalLossPoints(), base.totalPushPoints(),
                base.strikesAllowed(), limit, scope,
                java.time.LocalDate.now(), false);
    }

    private GroupSettings singlePickPerGame() {
        GroupSettings base = TestGroups.settings("One per game", Cadence.WEEKLY, 20,
                true, true, true);
        return new GroupSettings(
                base.name(), base.description(), base.visibility(), base.joinPassword(),
                base.groupType(), base.cadence(), base.lengthType(), base.startSeason(),
                base.lockLeadMinutes(), base.maxPicksPerCadence(), base.minPicksPerCadence(),
                false, base.requireApproval(), base.shareableByMembers(),
                base.moneylineEnabled(), base.spreadEnabled(), base.totalEnabled(),
                null, null, null, null, null, null,
                base.moneylineWinPoints(), base.moneylineLossPoints(), base.moneylinePushPoints(),
                base.spreadWinPoints(), base.spreadLossPoints(), base.spreadPushPoints(),
                base.totalWinPoints(), base.totalLossPoints(), base.totalPushPoints(),
                base.strikesAllowed(), base.teamPickLimit(), base.teamPickLimitScope(),
                java.time.LocalDate.now(), false);
    }

    /** Usernames are unique and cap at 20 characters, so owners are numbered. */
    private int owners;

    private Group group(GroupSettings settings) {
        UUID owner = UUID.randomUUID();
        String name = "owner" + owners++;
        users.save(new AppUser(owner, name + "@example.com", name, name));
        Group saved = groups.save(new Group(owner, settings));
        groupMembers.save(new GroupMember(saved.getId(), owner, GroupRole.OWNER));
        return saved;
    }

    private UUID member(Group group, String name) {
        UUID id = UUID.randomUUID();
        users.save(new AppUser(id, name + "@example.com", name, name));
        groupMembers.save(new GroupMember(group.getId(), id, GroupRole.MEMBER));
        return id;
    }

    /** Games that all share one home team, for the team-limit tests. */
    private Game sameHomeTeam(long id, int week) {
        Game game = game(id, week);
        game.setHomeTeam("Anytown State");
        return games.save(game);
    }

    private Game game(long id) {
        return game(id, 1);
    }

    private Game game(long id, int week) {
        return game(id, week, Instant.now().plus(3, ChronoUnit.DAYS));
    }

    private Game game(long id, int week, Instant kickoff) {
        Game game = new Game();
        game.setId(id);
        game.setSeason(2026);
        game.setWeek(week);
        game.setHomeTeam("Home " + id);
        game.setAwayTeam("Away " + id);
        game.setKickoff(kickoff);
        game.setHomeSpread(new BigDecimal("-7.5"));
        game.setOverUnder(new BigDecimal("45.5"));
        game.setStatus(GameStatus.SCHEDULED);
        return games.save(game);
    }
}
