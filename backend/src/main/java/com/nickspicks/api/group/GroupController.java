package com.nickspicks.api.group;

import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.web.ApiDtos.GroupDetail;
import com.nickspicks.api.web.ApiDtos.GroupMemberRow;
import com.nickspicks.api.web.ApiDtos.GroupSearchResult;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.ApiDtos.GroupSummary;
import com.nickspicks.api.web.ApiDtos.JoinGroupRequest;
import com.nickspicks.api.web.ApiDtos.JoinRequestRow;
import com.nickspicks.api.web.ApiDtos.JoinResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The member-facing half of groups: what I belong to, what I can find, and what
 * an owner can do to their own group.
 *
 * <p>Creation is not here - it is admin-only for now, in
 * {@link AdminGroupController}.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final CurrentUserService currentUser;
    private final GroupService groups;

    public GroupController(CurrentUserService currentUser, GroupService groups) {
        this.currentUser = currentUser;
        this.groups = groups;
    }

    /** Groups I am in. */
    @GetMapping("/mine")
    public List<GroupSummary> mine(@AuthenticationPrincipal Jwt jwt) {
        return groups.myGroups(currentUser.resolve(jwt));
    }

    /**
     * Public groups only. A blank term lists them all, which is what an empty
     * search box should do.
     */
    @GetMapping("/search")
    public List<GroupSearchResult> search(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) String q) {
        return groups.search(q, currentUser.resolve(jwt));
    }

    /** Full settings. Members and app admins only. */
    @GetMapping("/{id}")
    public GroupDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return groups.detail(id, currentUser.resolve(jwt));
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberRow> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return groups.members(id, currentUser.resolve(jwt));
    }

    /**
     * Joining. When the group requires approval this returns {@code pending}
     * with no detail - the caller is not in the group yet, so its settings are
     * not theirs to read.
     */
    @PostMapping("/{id}/join")
    public JoinResult join(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID id,
                           @RequestBody(required = false) JoinGroupRequest request) {
        AppUser caller = currentUser.resolve(jwt);
        GroupService.JoinOutcome outcome =
                groups.join(id, caller, request == null ? null : request.password());

        return outcome.pending()
                ? new JoinResult(true, null)
                : new JoinResult(false, groups.toDetail(outcome.group(), caller));
    }

    /**
     * This member's link to this group, created on first press.
     *
     * <p>The same link every time - one already sent has to keep working.
     */
    @PostMapping("/{id}/share")
    public ApiDtos.ShareLinkResponse share(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID id) {
        AppUser caller = currentUser.resolve(jwt);
        return new ApiDtos.ShareLinkResponse(groups.shareLink(id, caller).getToken());
    }

    /** Pins the group to the top of this member's picker. */
    @PutMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID id,
                         @RequestBody ApiDtos.FavoriteRequest request) {
        groups.setFavorite(id, currentUser.resolveId(jwt), request.favorite());
    }

    /** The owners' queue of people waiting to be let in. */
    @GetMapping("/{id}/requests")
    public List<JoinRequestRow> requests(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return groups.pendingRequestRows(id, currentUser.resolve(jwt));
    }

    @PostMapping("/{id}/requests/{userId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID id,
                        @PathVariable UUID userId) {
        groups.decideRequest(id, currentUser.resolve(jwt), userId, true);
    }

    @PostMapping("/{id}/requests/{userId}/deny")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deny(@AuthenticationPrincipal Jwt jwt,
                     @PathVariable UUID id,
                     @PathVariable UUID userId) {
        groups.decideRequest(id, currentUser.resolve(jwt), userId, false);
    }

    /**
     * Promotes a member to owner, or steps one back down. Owners only - and
     * the last owner cannot be demoted.
     */
    @PutMapping("/{id}/members/{userId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRole(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID id,
                        @PathVariable UUID userId,
                        @Valid @RequestBody ApiDtos.MemberRoleRequest request) {
        groups.setMemberRole(id, currentUser.resolve(jwt), userId, request.role());
    }

    /** Owner (or an app admin) edits settings. */
    @PutMapping("/{id}")
    public GroupDetail update(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @Valid @RequestBody GroupSettings settings) {
        AppUser caller = currentUser.resolve(jwt);
        return groups.toDetail(groups.update(id, caller, settings), caller);
    }

    /** Owner (or an app admin) deletes. Every pick in the group goes with it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.delete(id, currentUser.resolve(jwt));
    }

    /** An owner removes a member; a member removes themselves. */
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id,
                             @PathVariable UUID userId) {
        groups.removeMember(id, currentUser.resolve(jwt), userId);
    }
}
