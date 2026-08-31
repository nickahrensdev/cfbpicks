package com.nickspicks.api.group;

import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.ApiDtos.JoinGroupRequest;
import com.nickspicks.api.web.ApiDtos.JoinResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The receiving end of a share link.
 *
 * <p>Its own controller because it is the one group surface reachable without
 * an account. Someone deciding whether to sign up has to be able to see what
 * they are being invited to first, so {@code GET} is public - and being public
 * is exactly why it lives apart from {@link GroupController}, where every other
 * handler assumes a caller.
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final GroupService groups;
    private final CurrentUserService currentUser;

    public ShareController(GroupService groups, CurrentUserService currentUser) {
        this.groups = groups;
        this.currentUser = currentUser;
    }

    /**
     * What the invitation is for. No account needed, and nothing here that the
     * token does not already imply - not the join password, not the roster.
     */
    @GetMapping("/{token}")
    public ApiDtos.ShareInvite invite(@PathVariable String token) {
        return groups.invite(token);
    }

    /**
     * Credits whoever shared the link, and says where to send the caller.
     *
     * <p>Called once the invitee is signed in, which is the first moment there
     * is anyone to credit. Attribution happens here rather than at join time so
     * that someone who signs up through a link still counts even if they never
     * finish joining the group.
     */
    @PostMapping("/{token}/claim")
    public ApiDtos.ShareClaim claim(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable String token) {
        AppUser caller = currentUser.resolve(jwt);
        return groups.claim(token, caller);
    }

    /**
     * Joins through the link.
     *
     * <p>The one path into a private group that needs no owner to act, because
     * the link is itself an act by somebody already inside - and only exists
     * when the group's settings allowed it to be made.
     */
    @PostMapping("/{token}/join")
    public JoinResult join(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable String token,
                           @RequestBody(required = false) JoinGroupRequest request) {
        AppUser caller = currentUser.resolve(jwt);
        GroupService.JoinOutcome outcome =
                groups.joinByToken(token, caller, request == null ? null : request.password());

        return outcome.pending()
                ? new JoinResult(true, null)
                : new JoinResult(false, groups.toDetail(outcome.group(), caller));
    }
}
