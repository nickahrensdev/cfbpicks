package com.nickspicks.api.pick;

import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupType;
import com.nickspicks.api.group.TeamLimitScope;
import com.nickspicks.api.pick.PickExceptions.InvalidPickException;
import com.nickspicks.api.pick.PickExceptions.LineMovedException;
import com.nickspicks.api.pick.PickExceptions.PickWindowClosedException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nickspicks.api.web.ApiDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enforces the rules that make this a pick'em rather than a form: a cap on how
 * many picks a member holds in a period, and nothing changing once a game is
 * close to kickoff.
 *
 * <p>Every rule here now comes from the {@link Group} rather than from site
 * config, so two leagues can run the same season by different numbers. The
 * caller resolves the group and hands it in; this class never picks one for
 * you, because "the member's group" is a UI concept and guessing it here would
 * be how a pick lands in the wrong league.
 */
@Service
public class PickService {

    private final PickRepository picks;
    private final CadenceEntryRepository entries;
    private final GameRepository games;
    private final PickWindow window;
    private final PickAuditRepository audit;
    private final CadencePenaltyRepository penalties;

    public PickService(PickRepository picks, CadenceEntryRepository entries, GameRepository games,
                       PickWindow window, PickAuditRepository audit,
                       CadencePenaltyRepository penalties) {
        this.picks = picks;
        this.entries = entries;
        this.games = games;
        this.window = window;
        this.audit = audit;
        this.penalties = penalties;
    }

    @Transactional(readOnly = true)
    public List<Pick> findForUserWeek(UUID groupId, UUID userId, int season, int week) {
        return picks.findForUserWeek(groupId, userId, season, week);
    }

    /**
     * Picks left in the week, or null when there is no single number to give.
     *
     * <p>Null in two cases: the group sets no maximum, or its allowance is per
     * *day* - a week names several days, so no one figure describes what is
     * left. Returning null rather than a weekly lookup matters, because the
     * counter rows for a daily group are keyed by date and a weekly key would
     * silently miss them, reporting a full allowance forever while the server
     * went on rejecting picks.
     */
    @Transactional(readOnly = true)
    public Integer remainingPicks(Group group, UUID userId, int season, int week) {
        if (group.getMaxPicksPerCadence() == null || group.getCadence() == Cadence.DAILY) {
            return null;
        }
        int used = usedPicks(group, userId, season, week);
        return Math.max(0, group.getMaxPicksPerCadence() - used);
    }

    /** Weekly groups only - see {@link #remainingPicks}. */
    @Transactional(readOnly = true)
    public int usedPicks(Group group, UUID userId, int season, int week) {
        String periodKey = CadencePeriod.weekly(season, week);
        return entries.findById(new CadenceEntry.Key(group.getId(), userId, periodKey))
                .map(CadenceEntry::getPickCount)
                .orElse(0);
    }

    /**
     * Another member's card, with anything not yet kicked off stripped out.
     * Filtering happens here rather than in the UI - hiding it client-side
     * would still ship the data.
     *
     * @param week a single week, or null for the whole season. Null is the
     *             default the UI arrives with, because the current week has
     *             usually moved past the last week anyone actually picked -
     *             defaulting to it showed an empty card for a member with a
     *             full season behind them.
     */
    @Transactional(readOnly = true)
    public List<Pick> findRevealedForUser(UUID groupId, UUID userId, int season, Integer week) {
        Instant now = Instant.now();

        List<Pick> found = week == null
                ? picks.findForUserSeason(groupId, userId, season)
                : picks.findForUserWeek(groupId, userId, season, week);

        return found.stream()
                .filter(pick -> games.findById(pick.getGameId())
                        .map(game -> window.isRevealed(game, now))
                        .orElse(false))
                .toList();
    }

    /**
     * A member's picks on one day, in the game-day timezone.
     *
     * <p>What a daily group's board actually needs. Asking by week would mix
     * seven allowances into one number, which is why the budget bar used to
     * refuse to show a countdown at all for these groups.
     */
    @Transactional(readOnly = true)
    public List<Pick> findForUserDay(UUID groupId, UUID userId, LocalDate day) {
        ZonedDateTime start = day.atStartOfDay(CadencePeriod.GAME_DAY_ZONE);
        return picks.findForUserBetween(groupId, userId,
                start.toInstant(), start.plusDays(1).toInstant());
    }

    /**
     * What this member has left to do in one period, per market.
     *
     * <p>Built from picks already in hand rather than re-read, so the caller
     * that has just listed them does not pay for a second pass.
     */
    public List<ApiDtos.MarketBudget> marketBudgets(Group group, List<Pick> held) {
        List<ApiDtos.MarketBudget> budgets = new ArrayList<>();
        for (Market market : Market.values()) {
            boolean enabled = switch (market) {
                case SPREAD -> group.isSpreadEnabled();
                case TOTAL -> group.isTotalEnabled();
                case MONEYLINE -> group.isMoneylineEnabled();
            };
            if (!enabled) {
                continue;
            }
            long used = held.stream().filter(pick -> pick.getMarket() == market).count();
            budgets.add(new ApiDtos.MarketBudget(market, (int) used,
                    group.minFor(market), group.maxFor(market)));
        }
        return budgets;
    }

    @Transactional
    public Pick create(Group group, UUID userId, Long gameId, Selection selection) {
        return create(group, userId, gameId, selection, null);
    }

    /**
     * @param expectedLine the line the member was looking at, or null if the
     *                       caller does not care. Supplying it turns a stale
     *                       page into a visible conflict instead of a pick
     *                       silently made at a number they never saw.
     */
    @Transactional
    public Pick create(Group group, UUID userId, Long gameId, Selection selection,
                       BigDecimal expectedLine) {
        Game game = requireGame(gameId);
        Market market = selection.market();

        requireMarketEnabled(group, market);
        requireOpen(group, game, market);
        requireCurrentLine(game, market, expectedLine);

        CadenceEntry entry = lockEntry(group, userId, game);

        Integer max = group.getMaxPicksPerCadence();
        if (max != null && entry.getPickCount() >= max) {
            throw new WeeklyLimitReachedException(
                    "You already have %d picks for this %s"
                            .formatted(max, periodNoun(group)));
        }

        requireStillAlive(group, userId, game);
        requireMarketRoom(group, userId, game, market);
        requireTeamRoom(group, userId, game, selection);
        requireGameFree(group, userId, gameId);

        if (picks.findByGroupIdAndUserIdAndGameIdAndMarket(group.getId(), userId, gameId, market)
                .isPresent()) {
            throw new InvalidPickException(switch (market) {
                case TOTAL -> "You already picked the total on this game";
                case MONEYLINE -> "You already picked a moneyline on this game";
                case SPREAD -> "You already picked the spread on this game";
            });
        }

        Pick pick = new Pick();
        pick.setGroupId(group.getId());
        pick.setUserId(userId);
        pick.setGameId(gameId);
        pick.setSelection(selection);
        // Lock the line as it stands now; later movement will not affect it.
        pick.setLockedLine(window.line(game, market));

        entry.setPickCount(entry.getPickCount() + 1);
        entries.save(entry);

        try {
            Pick saved = picks.save(pick);
            audit.save(PickAudit.created(saved));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // The unique (group, user, game, market) index caught a duplicate
            // that slipped past the check above.
            throw new InvalidPickException("You already picked this game");
        }
    }

    @Transactional
    public Pick update(Group group, UUID userId, UUID pickId, Selection selection) {
        return update(group, userId, pickId, selection, null);
    }

    @Transactional
    public Pick update(Group group, UUID userId, UUID pickId, Selection selection,
                       BigDecimal expectedLine) {
        Pick pick = requireOwnedPick(group, userId, pickId);
        Game game = requireGame(pick.getGameId());
        Market market = pick.getMarket();

        // Switching markets is a different pick, not an edit - it would change
        // the row's identity under the (group, user, game, market) key. Cancel
        // and create instead.
        if (selection.market() != market) {
            throw new InvalidPickException(
                    "Cancel this pick and make a new one to switch markets");
        }

        requireOpen(group, game, market);
        requireCurrentLine(game, market, expectedLine);

        // Take the lock even though the count is unchanged, so an edit cannot
        // interleave with a concurrent create in the same period.
        lockEntry(group, userId, game);

        Selection previousSelection = pick.getSelection();
        BigDecimal previousLine = pick.getLockedLine();

        pick.setSelection(selection);
        // Editing re-locks the current line - the member is committing to
        // today's number, not the one they saw last week.
        pick.setLockedLine(window.line(game, market));
        pick.setUpdatedAt(Instant.now());

        Pick saved = picks.save(pick);
        audit.save(PickAudit.updated(saved, previousSelection, previousLine));
        return saved;
    }

    /**
     * Moves a pick onto the game's current line without changing sides.
     *
     * <p>Only allowed when the line actually improved for the side taken -
     * the button exists to take a freebie, and letting it re-lock a worse
     * number would be a trap. Still subject to the group's lock window.
     */
    @Transactional
    public Pick relock(Group group, UUID userId, UUID pickId) {
        Pick pick = requireOwnedPick(group, userId, pickId);
        Game game = requireGame(pick.getGameId());
        requireOpen(group, game, pick.getMarket());

        if (!window.isLineImproved(pick, game)) {
            throw new InvalidPickException(
                    "The current line is not better than the one you already have");
        }

        lockEntry(group, userId, game);

        Selection selection = pick.getSelection();
        BigDecimal previousLine = pick.getLockedLine();

        pick.setLockedLine(window.line(game, pick.getMarket()));
        pick.setUpdatedAt(Instant.now());

        Pick saved = picks.save(pick);
        audit.save(PickAudit.updated(saved, selection, previousLine));
        return saved;
    }

    /** Whether this pick could be re-locked at a better number right now. */
    @Transactional(readOnly = true)
    public boolean isLineImproved(Group group, Pick pick) {
        return games.findById(pick.getGameId())
                .map(game -> window.isOpen(game, group.getLockLeadMinutes())
                        && window.isLineImproved(pick, game))
                .orElse(false);
    }

    /** @return the pick's game, so the caller can hand back updated card state without a second read. */
    @Transactional
    public Game delete(Group group, UUID userId, UUID pickId) {
        Pick pick = requireOwnedPick(group, userId, pickId);
        Game game = requireGame(pick.getGameId());
        requireOpen(group, game, pick.getMarket());

        CadenceEntry entry = lockEntry(group, userId, game);
        entry.setPickCount(Math.max(0, entry.getPickCount() - 1));
        entries.save(entry);

        // Audit before delete so the row still has its final state to record.
        audit.save(PickAudit.cancelled(pick));
        picks.delete(pick);
        return game;
    }

    @Transactional(readOnly = true)
    public List<Pick> findForUserGame(UUID groupId, UUID userId, Long gameId) {
        return picks.findAllByGroupIdAndUserIdAndGameId(groupId, userId, gameId);
    }

    /**
     * The counter row for the period this game falls in, created on first use.
     * Locked pessimistically - see {@link CadenceEntry}.
     */
    private CadenceEntry lockEntry(Group group, UUID userId, Game game) {
        String periodKey = CadencePeriod.of(group, game);
        return entries.findAndLock(group.getId(), userId, periodKey)
                .orElseGet(() -> entries.save(new CadenceEntry(group.getId(), userId, periodKey)));
    }

    /**
     * The member's picks in the same period as this game.
     *
     * <p>Two paths because a period is two different things. A week is a column
     * on the game and filters directly; a day is a span of instants in the
     * game-day timezone, and has to be asked for that way. Both are bounded by
     * one member in one group in one period, so neither reads much.
     */
    private List<Pick> periodPicks(Group group, UUID userId, Game game) {
        if (group.getCadence() == Cadence.WEEKLY) {
            return picks.findForUserWeek(group.getId(), userId, game.getSeason(), game.getWeek());
        }
        ZonedDateTime dayStart = game.getKickoff()
                .atZone(CadencePeriod.GAME_DAY_ZONE)
                .toLocalDate()
                .atStartOfDay(CadencePeriod.GAME_DAY_ZONE);

        return picks.findForUserBetween(group.getId(), userId,
                dayStart.toInstant(), dayStart.plusDays(1).toInstant());
    }

    /**
     * Rejects a pick from someone already out of an elimination pool.
     *
     * <p>Counted the same way the standings count it - graded losses plus
     * charged minimums - so the board and this check can never disagree about
     * who is still in. Charged minimums have to count, or sitting out a week
     * would be the safest play in a pool whose premise is that you cannot.
     *
     * <p>Elimination groups are always PER_YEAR, so the season is the pool.
     */
    private void requireStillAlive(Group group, UUID userId, Game game) {
        if (group.getGroupType() != GroupType.ELIMINATION || group.getStrikesAllowed() == null) {
            return;
        }
        int season = game.getSeason();
        long losses = picks.countLosses(group.getId(), userId, season)
                + penalties.seasonShortfall(group.getId(), userId, String.valueOf(season));

        if (losses > group.getStrikesAllowed()) {
            throw new InvalidPickException(
                    "You are out for the season - %d losses, and this group allows %d"
                            .formatted(losses, group.getStrikesAllowed()));
        }
    }

    /**
     * Rejects a pick that would exceed this market's own allowance.
     *
     * <p>Separate from the overall cap because that one cannot tell markets
     * apart: a group with three markets, one scoring table and only a total cap
     * has a dominant strategy of taking heavy favourites to win and nothing
     * else. This is the setting that makes the other markets worth playing.
     */
    private void requireMarketRoom(Group group, UUID userId, Game game, Market market) {
        Integer max = group.maxFor(market);
        if (max == null) {
            return;
        }
        long held = periodPicks(group, userId, game).stream()
                .filter(pick -> pick.getMarket() == market)
                .count();

        if (held >= max) {
            throw new WeeklyLimitReachedException(max == 0
                    ? "This group does not allow %s picks".formatted(marketNoun(market))
                    : "You already have %d %s pick%s for this %s"
                            .formatted(max, marketNoun(market), max == 1 ? "" : "s",
                                    periodNoun(group)));
        }
    }

    /**
     * Rejects a pick on a team the member has already used up.
     *
     * <p>Counted across the season rather than the period: a limit that reset
     * every week would barely bind, and the point of the setting is to stop one
     * reliable team carrying a whole campaign. A continuous group has no reset
     * of its own, so the season is the only bound available there too.
     *
     * <p>Totals name no team, so they are never counted and never limited - the
     * scope enum has no option for them for the same reason.
     */
    private void requireTeamRoom(Group group, UUID userId, Game game, Selection selection) {
        Integer limit = group.getTeamPickLimit();
        if (limit == null || !inScope(group.getTeamPickLimitScope(), selection.market())) {
            return;
        }
        String team = teamOf(game, selection);
        if (team == null) {
            return;
        }

        List<Pick> season = picks.findForUserSeason(group.getId(), userId, game.getSeason());
        Map<Long, Game> byId = games.findAllById(
                        season.stream().map(Pick::getGameId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));

        long used = season.stream()
                .filter(pick -> inScope(group.getTeamPickLimitScope(), pick.getMarket()))
                .filter(pick -> {
                    Game picked = byId.get(pick.getGameId());
                    return picked != null && team.equals(teamOf(picked, pick.getSelection()));
                })
                .count();

        if (used >= limit) {
            throw new InvalidPickException(
                    "You have already picked %s %d time%s this season, which is this group's limit"
                            .formatted(team, limit, limit == 1 ? "" : "s"));
        }
    }

    /**
     * Rejects a second market on a game when the group only allows one.
     *
     * <p>The unique (group, user, game, market) key already stops the same
     * market twice, so this is only about spreading one game across several
     * markets - which a group can turn off to keep a slate's worth of picks
     * spread over a slate's worth of games.
     */
    private void requireGameFree(Group group, UUID userId, Long gameId) {
        if (!group.isMultiplePicksPerGame()
                && picks.existsByGroupIdAndUserIdAndGameId(group.getId(), userId, gameId)) {
            throw new InvalidPickException(
                    "This group allows one pick per game, and you already have one on this game");
        }
    }

    /** Whether a team limit with this scope counts picks in this market. */
    private boolean inScope(TeamLimitScope scope, Market market) {
        if (scope == null || market == Market.TOTAL) {
            return false;
        }
        return switch (scope) {
            case BOTH -> true;
            case MONEYLINE -> market == Market.MONEYLINE;
            case SPREAD -> market == Market.SPREAD;
        };
    }

    /** The team a selection names, or null for a total. */
    private String teamOf(Game game, Selection selection) {
        return selection.isHomeSide()
                .map(home -> home ? game.getHomeTeam() : game.getAwayTeam())
                .orElse(null);
    }

    private String marketNoun(Market market) {
        return switch (market) {
            case MONEYLINE -> "moneyline";
            case SPREAD -> "spread";
            case TOTAL -> "over/under";
        };
    }

    private void requireMarketEnabled(Group group, Market market) {
        boolean enabled = switch (market) {
            case SPREAD -> group.isSpreadEnabled();
            case TOTAL -> group.isTotalEnabled();
            case MONEYLINE -> group.isMoneylineEnabled();
        };
        if (!enabled) {
            throw new InvalidPickException(switch (market) {
                case TOTAL -> "This group does not play the over/under";
                case MONEYLINE -> "This group does not play moneylines";
                case SPREAD -> "This group does not play the spread";
            });
        }
    }

    /**
     * Rejects a pick placed against a line that has since moved.
     *
     * <p>Without this, a tab left open overnight commits the member to
     * whatever the number is now - which may be several points away from what
     * they were looking at when they clicked. Skipped when the caller sends
     * no expectation, so older clients and server-side callers still work.
     */
    private void requireCurrentLine(Game game, Market market, BigDecimal expectedLine) {
        BigDecimal current = window.line(game, market);

        if (expectedLine == null || current == null) {
            return;
        }
        // compareTo, not equals: -7.5 and -7.50 are the same line.
        if (expectedLine.compareTo(current) != 0) {
            throw new LineMovedException(
                    "The line moved to %s while this page was open".formatted(current), current);
        }
    }

    private void requireOpen(Group group, Game game, Market market) {
        if (!window.isOpen(game, group.getLockLeadMinutes())) {
            throw new PickWindowClosedException(
                    "Picks for %s at %s closed %d minutes before kickoff"
                            .formatted(game.getAwayTeam(), game.getHomeTeam(),
                                    group.getLockLeadMinutes()));
        }
        // Open, but this particular market has no number posted. Never fires
        // for MONEYLINE, which needs nothing posted.
        if (!window.hasLine(game, market)) {
            throw new InvalidPickException(market == Market.TOTAL
                    ? "No total is posted for this game yet"
                    : "No spread is posted for this game yet");
        }
    }

    private String periodNoun(Group group) {
        return switch (group.getCadence()) {
            case DAILY -> "day";
            case WEEKLY -> "week";
        };
    }

    private Game requireGame(Long gameId) {
        return games.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game %d not found".formatted(gameId)));
    }

    private Pick requireOwnedPick(Group group, UUID userId, UUID pickId) {
        Pick pick = picks.findById(pickId)
                .orElseThrow(() -> new NotFoundException("Pick %s not found".formatted(pickId)));
        if (!pick.getUserId().equals(userId) || !pick.getGroupId().equals(group.getId())) {
            // Same response as a missing pick - do not confirm that someone
            // else's pick id exists, or that it exists in another group.
            throw new NotFoundException("Pick %s not found".formatted(pickId));
        }
        return pick;
    }
}
