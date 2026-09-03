package com.nickspicks.api.group;

import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.user.Role;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.ApiDtos.GroupDetail;
import com.nickspicks.api.web.ApiDtos.GroupMemberRow;
import com.nickspicks.api.web.ApiDtos.GroupSearchResult;
import com.nickspicks.api.web.ApiDtos.GroupSummary;
import com.nickspicks.api.web.ForbiddenException;
import com.nickspicks.api.web.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Groups: creation, settings, membership and the rules about who may do what.
 *
 * <p>Authorization lives here rather than in the controllers so the two
 * entry points - the member-facing {@link GroupController} and the app-admin
 * {@link AdminGroupController} - cannot drift apart on who is allowed to
 * delete a group.
 */
@Service
public class GroupService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupJoinRequestRepository joinRequests;
    private final AppUserRepository users;
    private final GroupShareLinkRepository shareLinks;
    private final GroupReferralRepository referrals;

    public GroupService(GroupRepository groups, GroupMemberRepository members,
                        GroupJoinRequestRepository joinRequests, AppUserRepository users,
                        GroupShareLinkRepository shareLinks, GroupReferralRepository referrals) {
        this.groups = groups;
        this.members = members;
        this.joinRequests = joinRequests;
        this.users = users;
        this.shareLinks = shareLinks;
        this.referrals = referrals;
    }

    // --------------------------------------------------------- authorization

    public Group require(UUID groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group %s not found".formatted(groupId)));
    }

    /**
     * Owners configure their own group; app admins configure any group. Being
     * an app admin does not make you a member - it makes you staff.
     */
    public Group requireManageable(UUID groupId, AppUser caller) {
        Group group = require(groupId);
        if (!canManage(group, caller)) {
            throw new ForbiddenException("Only the group owner can change this group");
        }
        return group;
    }

    /** Readable by members, and by app admins for support. */
    public Group requireVisible(UUID groupId, AppUser caller) {
        Group group = require(groupId);
        boolean member = members.existsByGroupIdAndUserId(groupId, caller.getId());
        if (!member && !isAppAdmin(caller)) {
            throw new ForbiddenException("You are not a member of this group");
        }
        return group;
    }

    /**
     * The group a member is acting in, for the games, picks and leaderboard
     * pages.
     *
     * <p>Membership is required rather than mere visibility: reading a board is
     * one thing, but these endpoints also decide what may be picked, and an
     * app admin browsing a league they are not in should not be able to pick in
     * it. {@link #requireVisible} is the read-only counterpart.
     */
    public Group requirePlayable(UUID groupId, UUID userId) {
        Group group = require(groupId);
        if (!members.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
        return group;
    }

    /**
     * Authority is the OWNER role in group_member, not who created the group.
     * A group can have several owners, and its creator need not still be one.
     */
    private boolean canManage(Group group, AppUser caller) {
        // A personal board has no manageable settings at all - not for its
        // owner, and not for staff. Saying so here rather than only at each
        // mutation means the UI hides the controls instead of offering ones
        // that would be refused.
        if (group.isPersonal()) {
            return false;
        }
        return isAppAdmin(caller) || isOwner(group.getId(), caller.getId());
    }

    private boolean isOwner(UUID groupId, UUID userId) {
        return members.findByGroupIdAndUserId(groupId, userId)
                .map(member -> member.getRole() == GroupRole.OWNER)
                .orElse(false);
    }

    /**
     * Whether removing or demoting this member would leave the group with
     * nobody able to run it.
     */
    private boolean isLastOwner(UUID groupId, UUID userId) {
        List<GroupMember> owners = members.findAllByGroupIdAndRole(groupId, GroupRole.OWNER);
        return owners.size() == 1 && owners.get(0).getUserId().equals(userId);
    }

    private boolean isAppAdmin(AppUser caller) {
        return caller.getRole() == Role.ADMIN;
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Creates a group and enrols the creator as its owner in the same
     * transaction, so a group can never exist with nobody able to configure it.
     */
    @Transactional
    public Group create(AppUser creator, GroupSettings settings) {
        settings.validate();

        Group group = groups.save(new Group(creator.getId(), settings));
        members.save(new GroupMember(group.getId(), creator.getId(), GroupRole.OWNER));
        return group;
    }

    /**
     * The private board of one that comes with an account.
     *
     * <p>Called on first sight of a member - see {@code CurrentUserService} -
     * and safe to call again: it answers the existing board rather than making
     * a second. The unique index in V24 is the real guarantee, since two
     * requests arriving together would both find nothing here.
     *
     * <p>Settings come from {@link PersonalGroups} and are never taken from a
     * caller. There is no endpoint that reaches this.
     */
    @Transactional
    public Group ensurePersonalGroup(AppUser owner) {
        return groups.findByCreatedByAndPersonalTrue(owner.getId()).orElseGet(() -> {
            Group group = groups.save(
                    new Group(owner.getId(), PersonalGroups.settings(), true));
            members.save(new GroupMember(group.getId(), owner.getId(), GroupRole.OWNER));
            return group;
        });
    }

    /**
     * Refuses anything that would change a personal board or let someone else
     * near it.
     *
     * <p>One method rather than a check per call site, so a path added later
     * has an obvious thing to call - and so the reason is stated once.
     */
    private void refuseIfPersonal(Group group, String action) {
        if (group.isPersonal()) {
            throw new ForbiddenException(
                    "Your own board cannot be %s - it always keeps its default settings"
                            .formatted(action));
        }
    }

    @Transactional
    public Group update(UUID groupId, AppUser caller, GroupSettings settings) {
        settings.validate();

        // Before requireManageable, which now answers "only the owner can
        // change this group" for a personal board - true but unhelpful to the
        // owner reading it. The specific reason should win.
        refuseIfPersonal(require(groupId), "edited");
        Group group = requireManageable(groupId, caller);

        // The type is fixed once the group exists. Pickem and elimination score
        // and cap differently, so flipping an established league would
        // re-interpret picks that were made under the other set of rules -
        // silently, and with no way back to what the standings used to say.
        if (settings.groupType() != group.getGroupType()) {
            throw new GroupExceptions.InvalidGroupSettingsException(
                    "A group's type cannot be changed after it is created");
        }

        group.apply(settings);
        return groups.save(group);
    }

    /** Members cascade with the group; so, later, will its picks. */
    @Transactional
    public void delete(UUID groupId, AppUser caller) {
        // Losing it would leave the account with no board at all, and nothing
        // recreates one after first sight.
        refuseIfPersonal(require(groupId), "deleted");
        Group group = requireManageable(groupId, caller);
        groups.delete(group);
    }

    // ------------------------------------------------------------ membership

    /**
     * Joining by search. Private groups are not joinable this way at all - they
     * are unlisted, and being handed the id should not be enough.
     *
     * <p>The password is checked before approval is considered, so a wrong one
     * is refused immediately rather than sitting in an owner's queue. When the
     * group requires approval the result is a pending request; otherwise the
     * caller is a member when this returns.
     */
    @Transactional
    public JoinOutcome join(UUID groupId, AppUser caller, String password) {
        Group group = require(groupId);

        // Only the search-and-join path is restricted to public groups. A
        // share link is a deliberate invitation from someone already inside,
        // so it admits to a private group too - see joinByToken.
        if (group.getVisibility() != Visibility.PUBLIC) {
            throw new ForbiddenException("This group is private - the owner has to add you");
        }
        return admit(group, caller, password);
    }

    /**
     * The membership half of joining, shared by search-and-join and by a share
     * link. Everything here is true however the caller arrived: the password,
     * the approval queue, and not joining twice.
     */
    private JoinOutcome admit(Group group, AppUser caller, String password) {
        // Both join paths land here - search-and-join and a share link - so
        // this one check covers them together.
        refuseIfPersonal(group, "joined");

        if (members.existsByGroupIdAndUserId(group.getId(), caller.getId())) {
            throw new GroupExceptions.AlreadyMemberException("You are already in this group");
        }
        if (group.isPasswordProtected()) {
            if (password == null || password.isBlank()) {
                throw new GroupExceptions.PasswordRequiredException(
                        "This group needs a password to join");
            }
            if (!group.getJoinPassword().equals(password)) {
                throw new GroupExceptions.PasswordIncorrectException("That password is not right");
            }
        }

        if (group.isRequireApproval()) {
            return new JoinOutcome(requestToJoin(group, caller), group);
        }

        members.save(new GroupMember(group.getId(), caller.getId(), GroupRole.MEMBER));
        return new JoinOutcome(false, group);
    }

    /**
     * Members who could be added to this group, matching a search term.
     *
     * <p>Existing members are filtered out here rather than left for the caller
     * to notice: offering to add someone who is already in is an option that
     * can only fail.
     *
     * <p>A blank term returns the first twenty accounts rather than none, so
     * the picker has something to show before anyone types - which is the old
     * behaviour, just bounded.
     */
    @Transactional(readOnly = true)
    public List<ApiDtos.MemberOption> candidates(UUID groupId, String term) {
        require(groupId);
        Set<UUID> existing = members.findAllByGroupId(groupId).stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        return users.search(term == null ? "" : term.trim(), PageRequest.of(0, 20)).stream()
                .filter(user -> !existing.contains(user.getId()))
                .map(user -> new ApiDtos.MemberOption(user.getId(), user.getDisplayName(),
                        user.getUsername(), user.getEmail()))
                .toList();
    }

    // ---------------------------------------------------------------- sharing

    /**
     * This member's link to this group, created on first use.
     *
     * <p>The same link every time, deliberately. One already pasted into a
     * message has to keep working, and minting a fresh one per press would
     * break every link the member had ever sent.
     */
    @Transactional
    public GroupShareLink shareLink(UUID groupId, AppUser caller) {
        Group group = require(groupId);
        // Before the role checks below, because those let an app admin through
        // whatever the group says - and a personal board is the one case where
        // that must not hold. isShareableBy already returns false for one, but
        // the admin bypass would step straight over it.
        refuseIfPersonal(group, "shared");

        GroupRole role = members.findByGroupIdAndUserId(groupId, caller.getId())
                .map(GroupMember::getRole)
                .orElse(null);

        if (role == null) {
            throw new ForbiddenException("Join the group before sharing it");
        }
        if (!group.isShareableBy(role) && !isAppAdmin(caller)) {
            throw new ForbiddenException(
                    "This group is private and its owner has not allowed members to share it");
        }

        return shareLinks.findByGroupIdAndSharerId(groupId, caller.getId())
                .orElseGet(() -> shareLinks.save(new GroupShareLink(groupId, caller.getId())));
    }

    /**
     * What the link's landing page shows before anyone signs in.
     *
     * <p>Unauthenticated on purpose: someone following an invitation has to be
     * able to see what they are being invited to before deciding to make an
     * account. It carries nothing the token does not already imply - never the
     * join password, never who else is in.
     */
    @Transactional(readOnly = true)
    public ApiDtos.ShareInvite invite(String token) {
        GroupShareLink link = requireLink(token);
        Group group = require(link.getGroupId());

        return new ApiDtos.ShareInvite(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.isPasswordProtected(),
                group.isRequireApproval(),
                members.countByGroupId(group.getId()),
                users.findById(link.getSharerId()).map(AppUser::getUsername).orElse(null));
    }

    /**
     * Records who brought this member in, and says where to send them.
     *
     * <p>Attribution is once per person for ever, and never to themselves.
     * Crediting on every visit would let a member farm their own count by
     * passing a link around people who already have accounts, so the row is
     * written only when there is not one already.
     */
    @Transactional
    public ApiDtos.ShareClaim claim(String token, AppUser caller) {
        GroupShareLink link = requireLink(token);
        Group group = require(link.getGroupId());

        if (!link.getSharerId().equals(caller.getId())
                && !referrals.existsById(caller.getId())) {
            referrals.save(new GroupReferral(caller.getId(), group.getId(), link.getSharerId()));
        }

        boolean member = members.existsByGroupIdAndUserId(group.getId(), caller.getId());
        boolean pending = !member && joinRequests
                .findByGroupIdAndUserId(group.getId(), caller.getId())
                .map(request -> request.getStatus() == JoinRequestStatus.PENDING)
                .orElse(false);

        return new ApiDtos.ShareClaim(group.getId(), group.getName(), member, pending,
                group.isPasswordProtected());
    }

    /**
     * Joins through a share link.
     *
     * <p>The one path that admits to a private group without an owner acting,
     * because the link is itself an act by someone already inside - and only
     * exists at all when the group's settings allowed it to be made.
     */
    @Transactional
    public JoinOutcome joinByToken(String token, AppUser caller, String password) {
        GroupShareLink link = requireLink(token);
        return admit(require(link.getGroupId()), caller, password);
    }

    private GroupShareLink requireLink(String token) {
        return shareLinks.findByToken(token)
                .orElseThrow(() -> new NotFoundException("That invite link is not valid"));
    }

    /** @param pending true when an owner still has to approve. */
    public record JoinOutcome(boolean pending, Group group) {
    }

    /**
     * Queues a request, reusing this member's existing row rather than piling
     * up one per attempt - an owner wants a list of people waiting, not a log.
     */
    private boolean requestToJoin(Group group, AppUser caller) {
        GroupJoinRequest request = joinRequests
                .findByGroupIdAndUserId(group.getId(), caller.getId())
                .orElseGet(() -> new GroupJoinRequest(group.getId(), caller.getId()));

        if (request.getStatus() == JoinRequestStatus.PENDING && request.getId() != null) {
            throw new GroupExceptions.AlreadyMemberException(
                    "You have already asked to join this group");
        }
        request.reopen();
        joinRequests.save(request);
        return true;
    }

    @Transactional(readOnly = true)
    public List<GroupJoinRequest> pendingRequests(UUID groupId, AppUser caller) {
        requireManageable(groupId, caller);
        return joinRequests.findAllByGroupIdOrderByRequestedAtAsc(groupId).stream()
                .filter(request -> request.getStatus() == JoinRequestStatus.PENDING)
                .toList();
    }

    /** Approving enrols them; denying leaves the row so they are not re-queued. */
    @Transactional
    public void decideRequest(UUID groupId, AppUser caller, UUID userId, boolean approve) {
        requireManageable(groupId, caller);

        GroupJoinRequest request = joinRequests.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("No request from that member"));

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new GroupExceptions.AlreadyMemberException("That request has already been decided");
        }

        request.decide(approve ? JoinRequestStatus.APPROVED : JoinRequestStatus.DENIED,
                caller.getId());
        joinRequests.save(request);

        if (approve && !members.existsByGroupIdAndUserId(groupId, userId)) {
            members.save(new GroupMember(groupId, userId, GroupRole.MEMBER));
        }
    }

    /**
     * Promotes a member to owner, or steps one back down.
     *
     * <p>The last owner cannot be demoted: a group with no owner has nobody who
     * can configure it, approve anyone, or delete it.
     */
    @Transactional
    public void setMemberRole(UUID groupId, AppUser caller, UUID userId, GroupRole role) {
        refuseIfPersonal(require(groupId), "changed");
        requireManageable(groupId, caller);

        GroupMember member = members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("They are not in this group"));

        if (member.getRole() == role) {
            return;
        }
        if (role == GroupRole.MEMBER && isLastOwner(groupId, userId)) {
            throw new ForbiddenException(
                    "A group needs at least one owner - promote someone else first");
        }

        member.setRole(role);
        members.save(member);
    }

    /** Admin and owner path: add someone directly, no password. */
    @Transactional
    public void addMember(UUID groupId, AppUser caller, UUID userId) {
        // Closes the admin route in as well: a personal board has exactly one
        // member and nobody - staff included - may add another.
        refuseIfPersonal(require(groupId), "added to");
        requireManageable(groupId, caller);

        if (!users.existsById(userId)) {
            throw new NotFoundException("User %s not found".formatted(userId));
        }
        if (members.existsByGroupIdAndUserId(groupId, userId)) {
            throw new GroupExceptions.AlreadyMemberException("They are already in this group");
        }
        members.save(new GroupMember(groupId, userId, GroupRole.MEMBER));
    }

    /**
     * Owners and app admins remove anyone; a member may remove themselves. The
     * owner's own row is refused either way - a group with no owner has nobody
     * who can configure or delete it, so ownership has to move first.
     */
    @Transactional
    public void removeMember(UUID groupId, AppUser caller, UUID userId) {
        Group group = require(groupId);
        // Including leaving your own board, which would strand the group with
        // no members and the account with nothing to pick in.
        refuseIfPersonal(group, "left");

        boolean leavingSelf = caller.getId().equals(userId);
        if (!leavingSelf && !canManage(group, caller)) {
            throw new ForbiddenException("Only the group owner can remove members");
        }

        GroupMember member = members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("They are not in this group"));

        // Owners can leave now that there can be more than one; only the
        // last one is pinned, because the group would be left unmanageable.
        if (member.getRole() == GroupRole.OWNER && isLastOwner(groupId, userId)) {
            throw new ForbiddenException(
                    "The last owner cannot be removed - promote someone else, or delete the group");
        }
        members.delete(member);
    }

    // --------------------------------------------------------------- reading

    /** Groups the caller belongs to. */
    @Transactional(readOnly = true)
    public List<GroupSummary> myGroups(AppUser caller) {
        List<UUID> groupIds = members.findAllByUserId(caller.getId()).stream()
                .map(GroupMember::getGroupId)
                .toList();

        return summaries(groups.findAllById(groupIds), caller);
    }

    /** Every group. Admin screens only. */
    @Transactional(readOnly = true)
    public List<GroupSummary> allGroups(AppUser caller) {
        return summaries(groups.findAllByOrderByNameAsc(), caller);
    }

    /**
     * Public groups matching a name fragment. Never returns a private group, and
     * never the join password - only whether one is needed.
     */
    @Transactional(readOnly = true)
    public List<GroupSearchResult> search(String term, AppUser caller) {
        List<Group> found = groups.searchPublic(term == null ? "" : term.trim());

        Map<UUID, Long> counts = memberCounts(found);
        Map<UUID, String> creatorNames = creatorNames(found);
        Set<UUID> mine = members.findAllByUserId(caller.getId()).stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toSet());

        return found.stream()
                .map(group -> new GroupSearchResult(
                        group.getId(),
                        group.getName(),
                        group.getDescription(),
                        group.isPasswordProtected(),
                        counts.getOrDefault(group.getId(), 0L),
                        creatorNames.getOrDefault(group.getCreatedBy(), "unknown"),
                        mine.contains(group.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail detail(UUID groupId, AppUser caller) {
        Group group = requireVisible(groupId, caller);
        return toDetail(group, caller);
    }

    @Transactional(readOnly = true)
    public GroupDetail toDetail(Group group, AppUser caller) {
        boolean manageable = canManage(group, caller);
        GroupRole myRole = members.findByGroupIdAndUserId(group.getId(), caller.getId())
                .map(GroupMember::getRole)
                .orElse(null);

        return new GroupDetail(
                group.getId(),
                group.getCreatedBy(),
                displayName(group.getCreatedBy()),
                members.countByGroupId(group.getId()),
                manageable,
                myRole,
                // Only someone who can act on them is told how many there are.
                manageable
                        ? joinRequests.countByGroupIdAndStatus(group.getId(),
                                JoinRequestStatus.PENDING)
                        : 0,
                group.isShareableBy(myRole),
                group.isPersonal(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                settingsOf(group, manageable));
    }

    @Transactional(readOnly = true)
    public List<GroupMemberRow> members(UUID groupId, AppUser caller) {
        Group group = requireVisible(groupId, caller);

        List<GroupMember> rows = members.findAllByGroupId(groupId);
        Map<UUID, AppUser> byId = users.findAllById(rows.stream().map(GroupMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, user -> user));

        return rows.stream()
                .map(row -> {
                    AppUser user = byId.get(row.getUserId());
                    return new GroupMemberRow(
                            row.getUserId(),
                            user == null ? "deleted member" : user.getDisplayName(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getEmail(),
                            row.getRole(),
                            row.getUserId().equals(group.getCreatedBy()),
                            row.getJoinedAt());
                })
                // Owner first, then alphabetically - the owner is who you look for.
                .sorted((a, b) -> {
                    if (a.role() != b.role()) {
                        return a.role() == GroupRole.OWNER ? -1 : 1;
                    }
                    return a.displayName().compareToIgnoreCase(b.displayName());
                })
                .toList();
    }

    /**
     * Pins or unpins a group for this member. Membership is required - you
     * cannot favourite a league you are not in.
     */
    @Transactional
    public void setFavorite(UUID groupId, UUID userId, boolean favorite) {
        GroupMember member = members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));
        member.setFavorite(favorite);
        members.save(member);
    }

    /** The queue an owner sees on the Requests tab. */
    @Transactional(readOnly = true)
    public List<ApiDtos.JoinRequestRow> pendingRequestRows(UUID groupId, AppUser caller) {
        List<GroupJoinRequest> pending = pendingRequests(groupId, caller);

        Map<UUID, AppUser> byId = users
                .findAllById(pending.stream().map(GroupJoinRequest::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, user -> user));

        return pending.stream()
                .map(request -> {
                    AppUser user = byId.get(request.getUserId());
                    return new ApiDtos.JoinRequestRow(
                            request.getUserId(),
                            user == null ? "deleted member" : user.getDisplayName(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getEmail(),
                            request.getRequestedAt());
                })
                .toList();
    }

    // --------------------------------------------------------------- mapping

    /**
     * The settings as the edit form wants them back. The join password is
     * included only for someone who may manage the group - it is stored as
     * entered precisely so an owner can read it back and share it.
     */
    private GroupSettings settingsOf(Group group, boolean includePassword) {
        return new GroupSettings(
                group.getName(),
                group.getDescription(),
                group.getVisibility(),
                includePassword ? group.getJoinPassword() : null,
                group.getGroupType(),
                group.getCadence(),
                group.getLengthType(),
                group.getStartSeason(),
                group.getLockLeadMinutes(),
                group.getMaxPicksPerCadence(),
                group.getMinPicksPerCadence(),
                group.isMultiplePicksPerGame(),
                group.isRequireApproval(),
                group.isShareableByMembers(),
                group.isMoneylineEnabled(),
                group.isSpreadEnabled(),
                group.isTotalEnabled(),
                group.getMoneylineMinPerCadence(),
                group.getMoneylineMaxPerCadence(),
                group.getSpreadMinPerCadence(),
                group.getSpreadMaxPerCadence(),
                group.getTotalMinPerCadence(),
                group.getTotalMaxPerCadence(),
                group.getMoneylineWinPoints(),
                group.getMoneylineLossPoints(),
                group.getMoneylinePushPoints(),
                group.getSpreadWinPoints(),
                group.getSpreadLossPoints(),
                group.getSpreadPushPoints(),
                group.getTotalWinPoints(),
                group.getTotalLossPoints(),
                group.getTotalPushPoints(),
                group.getStrikesAllowed(),
                group.getTeamPickLimit(),
                group.getTeamPickLimitScope());
    }

    private List<GroupSummary> summaries(Collection<Group> found, AppUser caller) {
        Map<UUID, Long> counts = memberCounts(found);
        Map<UUID, String> creatorNames = creatorNames(found);
        // The whole membership row, since the summary needs both the role and
        // whether this member has favourited the group.
        Map<UUID, GroupMember> mine = members.findAllByUserId(caller.getId()).stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, row -> row));

        return found.stream()
                .map(group -> new GroupSummary(
                        group.getId(),
                        group.getName(),
                        group.getDescription(),
                        group.getVisibility(),
                        group.getGroupType(),
                        group.getCadence(),
                        group.getLengthType(),
                        group.getStartSeason(),
                        group.getLockLeadMinutes(),
                        group.isMoneylineEnabled(),
                        group.isSpreadEnabled(),
                        group.isTotalEnabled(),
                        group.getCreatedBy(),
                        creatorNames.getOrDefault(group.getCreatedBy(), "unknown"),
                        counts.getOrDefault(group.getId(), 0L),
                        canManage(group, caller),
                        Optional.ofNullable(mine.get(group.getId()))
                                .map(GroupMember::getRole)
                                .orElse(null),
                        Optional.ofNullable(mine.get(group.getId()))
                                .map(GroupMember::isFavorite)
                                .orElse(false),
                        group.isShareableBy(Optional.ofNullable(mine.get(group.getId()))
                                .map(GroupMember::getRole)
                                .orElse(null)),
                        group.isPersonal(),
                        group.getCreatedAt()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    /** One query for every count, rather than one per row. */
    private Map<UUID, Long> memberCounts(Collection<Group> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        members.countByGroupIds(found.stream().map(Group::getId).toList())
                .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        return counts;
    }

    private Map<UUID, String> creatorNames(Collection<Group> found) {
        List<UUID> ids = found.stream()
                .map(Group::getCreatedBy)
                // Null once the creator's account is deleted - the group lives on.
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getUsername));
    }

    /** The creator is shown as a handle, so this returns the username. */
    private String displayName(UUID userId) {
        return Optional.ofNullable(userId)
                .flatMap(users::findById)
                .map(AppUser::getUsername)
                .orElse("unknown");
    }
}
