package com.nickspicks.api.group;

import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.web.ApiDtos;
import com.nickspicks.api.web.ApiDtos.GroupDetail;
import com.nickspicks.api.web.ApiDtos.GroupMemberRow;
import com.nickspicks.api.web.ApiDtos.GroupSummary;
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
 * Group administration for app admins: create any group, and manage the members
 * of any group.
 *
 * <p>Creation lives here because {@code changes.md} makes groups admin-only for
 * now. When that opens up to everyone the handler moves to
 * {@link GroupController} and drops the {@code requireAdmin} line - nothing else
 * about it changes.
 *
 * <p>As everywhere else in this codebase the admin gate is a first-line
 * {@code requireAdmin} call rather than an annotation. Every method here needs
 * one.
 */
@RestController
@RequestMapping("/api/admin/groups")
public class AdminGroupController {

    private final CurrentUserService currentUser;
    private final GroupService groups;

    public AdminGroupController(CurrentUserService currentUser, GroupService groups) {
        this.currentUser = currentUser;
        this.groups = groups;
    }

    @GetMapping
    public List<GroupSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return groups.allGroups(currentUser.requireAdmin(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail create(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody GroupSettings settings) {
        AppUser admin = currentUser.requireAdmin(jwt);
        return groups.toDetail(groups.create(admin, settings), admin);
    }

    @GetMapping("/{id}")
    public GroupDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AppUser admin = currentUser.requireAdmin(jwt);
        return groups.toDetail(groups.require(id), admin);
    }

    @PutMapping("/{id}")
    public GroupDetail update(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @Valid @RequestBody GroupSettings settings) {
        AppUser admin = currentUser.requireAdmin(jwt);
        return groups.toDetail(groups.update(id, admin, settings), admin);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.delete(id, currentUser.requireAdmin(jwt));
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberRow> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return groups.members(id, currentUser.requireAdmin(jwt));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id,
                             @PathVariable UUID userId) {
        groups.removeMember(id, currentUser.requireAdmin(jwt), userId);
    }
}
