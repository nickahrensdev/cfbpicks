package com.nickspicks.api.pick;

import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Charges the minimums a period closed without meeting.
 *
 * <p>Maximums are enforced when a pick is made - the pick that would break one
 * is simply refused. A minimum cannot work that way, because a member with no
 * picks yet has not broken it; they are early. So it is settled instead: once a
 * period stops accepting picks, whoever finished short is charged the
 * difference as losses.
 *
 * <p>A period is closed when its last game has kicked off. That is the moment
 * no further pick can change anyone's count - deliberately earlier than the
 * moment the games <em>finish</em>, because a shortfall does not depend on any
 * result and there is no reason to make people wait for one.
 *
 * <p>Every run is idempotent: a settled period is recorded and skipped
 * thereafter, so the job can run as often as it likes without charging the same
 * failure twice.
 */
@Service
public class CadenceSettlementService {

    private static final Logger log = LoggerFactory.getLogger(CadenceSettlementService.class);

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GameRepository games;
    private final PickRepository picks;
    private final CadenceSettlementRepository settlements;
    private final CadencePenaltyRepository penalties;

    public CadenceSettlementService(GroupRepository groups, GroupMemberRepository members,
                                    GameRepository games, PickRepository picks,
                                    CadenceSettlementRepository settlements,
                                    CadencePenaltyRepository penalties) {
        this.groups = groups;
        this.members = members;
        this.games = games;
        this.picks = picks;
        this.settlements = settlements;
        this.penalties = penalties;
    }

    /** @return how many periods were closed out across every group. */
    public int settleAll(int season) {
        int closed = 0;
        for (Group group : groups.findAll()) {
            try {
                closed += settle(group, season);
            } catch (RuntimeException ex) {
                // One misconfigured group must not stop the rest being settled.
                log.error("Settling group {} failed", group.getId(), ex);
            }
        }
        return closed;
    }

    /**
     * Closes out every period of this season that is finished and not yet
     * settled.
     *
     * @return how many periods were closed
     */
    @Transactional
    public int settle(Group group, int season) {
        if (!hasAnyMinimum(group)) {
            // Nothing to charge, and marking periods settled would only make
            // it harder to start charging later.
            return 0;
        }

        Map<String, List<Game>> byPeriod = new HashMap<>();
        for (Game game : games.findAllBySeason(season)) {
            if (game.getKickoff() != null) {
                byPeriod.computeIfAbsent(CadencePeriod.of(group, game), key -> new ArrayList<>())
                        .add(game);
            }
        }

        Set<String> alreadySettled = settlements.settledKeys(group.getId());
        Instant now = Instant.now();
        int closed = 0;

        for (Map.Entry<String, List<Game>> period : byPeriod.entrySet()) {
            if (alreadySettled.contains(period.getKey()) || !isClosed(period.getValue(), now)) {
                continue;
            }
            settlePeriod(group, period.getKey(), period.getValue());
            settlements.save(new CadenceSettlement(group.getId(), period.getKey()));
            closed++;
        }
        return closed;
    }

    /** A period is closed once nothing in it can still be picked. */
    private boolean isClosed(List<Game> period, Instant now) {
        return period.stream()
                .map(Game::getKickoff)
                .allMatch(kickoff -> kickoff.isBefore(now));
    }

    private void settlePeriod(Group group, String periodKey, List<Game> period) {
        Set<Long> gameIds = period.stream().map(Game::getId).collect(java.util.stream.Collectors.toSet());

        for (UUID userId : members.findAllByGroupId(group.getId()).stream()
                .map(com.nickspicks.api.group.GroupMember::getUserId)
                .toList()) {

            List<Pick> held = picks.findAllByGroupIdAndUserId(group.getId(), userId).stream()
                    .filter(pick -> gameIds.contains(pick.getGameId()))
                    .toList();

            for (Market market : Market.values()) {
                if (!enabled(group, market)) {
                    continue;
                }
                Integer min = group.minFor(market);
                if (min == null || min == 0) {
                    continue;
                }
                long made = held.stream().filter(pick -> pick.getMarket() == market).count();
                charge(group, userId, periodKey, market, (int) (min - made), lossPoints(group, market));
            }

            // The overall minimum, which names no market of its own.
            int overallMin = group.getMinPicksPerCadence();
            if (overallMin > 0) {
                charge(group, userId, periodKey, null, overallMin - held.size(),
                        harshestLossPoints(group));
            }
        }
    }

    /**
     * Writes one penalty, or updates the one already there.
     *
     * <p>Re-settling is not the normal path - a settled period is skipped - but
     * an admin re-run has to converge on the same answer rather than stack a
     * second charge on top of the first.
     */
    private void charge(Group group, UUID userId, String periodKey, Market market,
                        int shortfall, BigDecimal perLoss) {
        if (shortfall <= 0) {
            return;
        }
        BigDecimal cost = perLoss.multiply(BigDecimal.valueOf(shortfall));

        CadencePenalty existing = penalties
                .findAllByGroupIdAndPeriodKey(group.getId(), periodKey).stream()
                .filter(row -> row.getUserId().equals(userId) && row.getMarket() == market)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.restate(shortfall, cost);
            penalties.save(existing);
            return;
        }
        penalties.save(new CadencePenalty(group.getId(), userId, periodKey, market,
                shortfall, cost));
    }

    /**
     * What one missed pick costs when no market is named.
     *
     * <p>The harshest loss value the group uses, so that skipping a pick can
     * never be a better outcome than making a bad one. Any other choice makes
     * sitting out a strategy in a group that scores a loss below zero.
     */
    private BigDecimal harshestLossPoints(Group group) {
        BigDecimal worst = null;
        for (Market market : Market.values()) {
            if (!enabled(group, market)) {
                continue;
            }
            BigDecimal points = lossPoints(group, market);
            worst = worst == null || points.compareTo(worst) < 0 ? points : worst;
        }
        return worst == null ? BigDecimal.ZERO : worst;
    }

    private BigDecimal lossPoints(Group group, Market market) {
        return switch (market) {
            case SPREAD -> group.getSpreadLossPoints();
            case TOTAL -> group.getTotalLossPoints();
            case MONEYLINE -> group.getMoneylineLossPoints();
        };
    }

    private boolean enabled(Group group, Market market) {
        return switch (market) {
            case SPREAD -> group.isSpreadEnabled();
            case TOTAL -> group.isTotalEnabled();
            case MONEYLINE -> group.isMoneylineEnabled();
        };
    }

    private boolean hasAnyMinimum(Group group) {
        if (group.getMinPicksPerCadence() > 0) {
            return true;
        }
        for (Market market : Market.values()) {
            Integer min = group.minFor(market);
            if (enabled(group, market) && min != null && min > 0) {
                return true;
            }
        }
        return false;
    }
}
