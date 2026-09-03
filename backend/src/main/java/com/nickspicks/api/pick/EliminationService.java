package com.nickspicks.api.pick;

import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clears out what an eliminated member is still holding.
 *
 * <p>Picks may be made as far ahead as the schedule goes - nothing scopes them
 * to the current period - which is deliberate: in a daily pool you should be
 * able to set the week out in front of you rather than having to appear every
 * morning. The consequence is that a member can be knocked out while holding
 * picks on games that have not kicked off yet.
 *
 * <p>{@code PickService} refuses a <em>new</em> pick from someone already out,
 * but it runs when a pick is made, and elimination happens later - when a game
 * grades, or when a period is settled. So those two moments have to come back
 * and take away what the member had already placed. Without it an eliminated
 * member's record keeps moving for weeks, which reads as though they are still
 * playing.
 *
 * <p>Only unstarted games. A pick on a game already underway was made while
 * they were alive and stands, whatever it finishes as - removing it would
 * rewrite history rather than stop them playing on.
 */
@Service
public class EliminationService {

    private static final Logger log = LoggerFactory.getLogger(EliminationService.class);

    private final PickRepository picks;
    private final CadencePenaltyRepository penalties;

    public EliminationService(PickRepository picks, CadencePenaltyRepository penalties) {
        this.picks = picks;
        this.penalties = penalties;
    }

    /**
     * Drops every not-yet-started pick this member holds, if this group
     * eliminates and they are now out of it.
     *
     * <p>Idempotent, and cheap when it does not apply: both callers can invoke
     * it for the same member more than once, and a member who is still alive
     * costs one count query.
     *
     * @return how many picks were removed
     */
    @Transactional
    public int dropFuturePicksIfEliminated(Group group, UUID userId, int season) {
        if (group.getGroupType() != GroupType.ELIMINATION || group.getStrikesAllowed() == null) {
            return 0;
        }

        // Counted exactly as PickService and the standings count it - graded
        // losses plus charged minimums - so all three agree on who is out.
        long losses = picks.countLosses(group.getId(), userId, season)
                + penalties.seasonShortfall(group.getId(), userId, String.valueOf(season));

        if (losses <= group.getStrikesAllowed()) {
            return 0;
        }

        List<Pick> holding = picks.findUnstartedForUser(group.getId(), userId, Instant.now());
        if (holding.isEmpty()) {
            return 0;
        }

        picks.deleteAll(holding);
        log.info("Dropped {} unstarted picks for eliminated member {} in group {}",
                holding.size(), userId, group.getId());
        return holding.size();
    }
}
