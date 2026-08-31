package com.nickspicks.api.group;

import com.nickspicks.api.leaderboard.LeaderboardService;
import com.nickspicks.api.season.CurrentWeekResolver;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.web.ApiDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The leagues one member plays in, for their profile card.
 *
 * <p>Scoped to the leagues the <em>viewer</em> is also in. A member's card is
 * reachable by anyone signed in - that was a deliberate decision - but which
 * other leagues someone plays in is not public just because their picks in a
 * shared one are. Intersecting the two memberships is what keeps the card from
 * being a directory of everybody's private groups.
 */
@RestController
@RequestMapping("/api/members")
public class MemberGroupsController {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final LeaderboardService standings;
    private final CurrentWeekResolver weeks;
    private final CurrentUserService currentUser;

    public MemberGroupsController(GroupRepository groups, GroupMemberRepository members,
                                  LeaderboardService standings, CurrentWeekResolver weeks,
                                  CurrentUserService currentUser) {
        this.groups = groups;
        this.members = members;
        this.standings = standings;
        this.weeks = weeks;
        this.currentUser = currentUser;
    }

    @GetMapping("/{userId}/groups")
    public List<ApiDtos.MemberGroupRow> memberGroups(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID userId) {
        UUID viewer = currentUser.resolveId(jwt);

        Set<UUID> mine = members.findAllByUserId(viewer).stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toSet());

        List<GroupMember> theirs = members.findAllByUserId(userId).stream()
                .filter(row -> mine.contains(row.getGroupId()))
                .toList();

        int season = weeks.currentSeason();
        List<ApiDtos.MemberGroupRow> rows = new ArrayList<>(theirs.size());

        for (GroupMember membership : theirs) {
            Group group = groups.findById(membership.getGroupId()).orElse(null);
            if (group == null) {
                continue;
            }

            // Their row on that group's board. A continuous group has no season
            // filter of its own, but asking for this one still describes the
            // campaign anyone reading the card is actually watching.
            ApiDtos.StandingsRow standing = standings.standings(group, season, null).stream()
                    .filter(row -> row.userId().equals(userId))
                    .findFirst()
                    .orElse(null);

            rows.add(new ApiDtos.MemberGroupRow(
                    group.getId(),
                    group.getName(),
                    group.getGroupType(),
                    group.getCadence(),
                    membership.getRole(),
                    members.countByGroupId(group.getId()),
                    standing == null ? null : standing.rank(),
                    standing == null ? 0 : standing.wins(),
                    standing == null ? 0 : standing.losses(),
                    standing == null ? 0 : standing.pushes(),
                    standing == null ? 0 : standing.points()));
        }

        rows.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return rows;
    }
}
