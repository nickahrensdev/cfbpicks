package com.nickspicks.api.admin;

import com.nickspicks.api.game.Game;
import com.nickspicks.api.game.GameRepository;
import com.nickspicks.api.pick.PickAudit;
import com.nickspicks.api.pick.PickAuditRepository;
import com.nickspicks.api.pick.PickRepository;
import com.nickspicks.api.security.CurrentUserService;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import com.nickspicks.api.user.Role;
import com.nickspicks.api.web.ForbiddenException;
import com.nickspicks.api.web.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupReferralRepository;
import com.nickspicks.api.group.GroupRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User management and the pick activity feed. Admin role required for
 * everything here.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CurrentUserService currentUser;
    private final AppUserRepository users;
    private final PickRepository picks;
    private final PickAuditRepository audits;
    private final GameRepository games;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupReferralRepository referrals;

    public AdminController(CurrentUserService currentUser, AppUserRepository users,
                           PickRepository picks, PickAuditRepository audits,
                           GameRepository games, GroupRepository groups,
                           GroupMemberRepository members, GroupReferralRepository referrals) {
        this.currentUser = currentUser;
        this.users = users;
        this.picks = picks;
        this.audits = audits;
        this.games = games;
        this.groups = groups;
        this.members = members;
        this.referrals = referrals;
    }

    /** Turns a repository's (id, count) pairs into a lookup. */
    private static Map<UUID, Long> tally(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    // ----------------------------------------------------------------- users

    public record UserRow(UUID id, String displayName, String username, String email, Role role,
                          long totalPicks,
                          /** Groups this member created. Survives them losing ownership of one. */
                          long groupsCreated,
                          /** Groups they are currently in, whatever their role. */
                          long groupsJoined,
                          /** People who first reached the site through one of their links. */
                          long referrals,
                          Instant createdAt) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    @GetMapping("/users")
    public List<UserRow> listUsers(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);

        // Four aggregates, one grouped query each, rather than four counts per
        // member - the list is every account on the site, so per-row queries
        // would grow with it.
        Map<UUID, Long> pickCounts = tally(picks.countByUser());
        Map<UUID, Long> created = tally(groups.countByCreator());
        Map<UUID, Long> joined = tally(members.countByMember());
        Map<UUID, Long> referred = tally(referrals.countBySharer());

        return users.findAll().stream()
                .map(user -> new UserRow(user.getId(), user.getDisplayName(),
                        user.getUsername(), user.getEmail(),
                        user.getRole(), pickCounts.getOrDefault(user.getId(), 0L),
                        created.getOrDefault(user.getId(), 0L),
                        joined.getOrDefault(user.getId(), 0L),
                        referred.getOrDefault(user.getId(), 0L),
                        user.getCreatedAt()))
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()))
                .toList();
    }

    @PutMapping("/users/{id}/role")
    @Transactional
    public UserRow changeRole(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @Valid @RequestBody RoleRequest request) {
        AppUser admin = currentUser.requireAdmin(jwt);

        if (admin.getId().equals(id) && request.role() != Role.ADMIN) {
            // Blocking self-demotion keeps the site from ending up with zero
            // admins by accident.
            throw new ForbiddenException("You cannot remove your own admin role");
        }

        AppUser target = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User %s not found".formatted(id)));
        target.setRole(request.role());
        users.save(target);

        // Counts are zero here rather than recomputed: the page reloads the
        // whole list after a role change, so this response only has to carry
        // the role that actually changed.
        return new UserRow(target.getId(), target.getDisplayName(), target.getUsername(),
                target.getEmail(),
                target.getRole(), 0L, 0L, 0L, 0L, target.getCreatedAt());
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AppUser admin = currentUser.requireAdmin(jwt);

        if (admin.getId().equals(id)) {
            throw new ForbiddenException("You cannot delete your own account from here");
        }
        if (!users.existsById(id)) {
            throw new NotFoundException("User %s not found".formatted(id));
        }
        // Picks, weekly entries and audit rows cascade with the user.
        users.deleteById(id);
    }

    // -------------------------------------------------------------- activity

    public record ActivityRow(Long id, Instant at, UUID userId, String displayName,
                              String username,
                              PickAudit.Action action, Long gameId, String game,
                              String market, String selection, BigDecimal lockedLine,
                              String previousSelection, BigDecimal previousLockedLine) {
    }

    /** Recent pick activity - every creation, edit and cancellation. */
    @GetMapping("/activity")
    public List<ActivityRow> activity(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID userId,
                                      @RequestParam(defaultValue = "50") int limit) {
        currentUser.requireAdmin(jwt);

        int capped = Math.min(Math.max(limit, 1), 200);
        List<PickAudit> rows = userId == null
                ? audits.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped))
                : audits.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, capped));

        Map<UUID, AppUser> byId = users.findAll().stream()
                .collect(Collectors.toMap(AppUser::getId, user -> user));

        Map<Long, String> gameLabels = new HashMap<>();
        games.findAllById(rows.stream().map(PickAudit::getGameId).distinct().toList())
                .forEach(game -> gameLabels.put(game.getId(), label(game)));

        return rows.stream()
                .map(row -> new ActivityRow(
                        row.getId(),
                        row.getCreatedAt(),
                        row.getUserId(),
                        Optional.ofNullable(byId.get(row.getUserId()))
                                .map(AppUser::getDisplayName).orElse("deleted member"),
                        Optional.ofNullable(byId.get(row.getUserId()))
                                .map(AppUser::getUsername).orElse(null),
                        row.getAction(),
                        row.getGameId(),
                        gameLabels.getOrDefault(row.getGameId(), "game " + row.getGameId()),
                        row.getMarket() == null ? null : row.getMarket().name(),
                        row.getSelection().name(),
                        row.getLockedLine(),
                        row.getPreviousSelection() == null ? null : row.getPreviousSelection().name(),
                        row.getPreviousLockedLine()))
                .toList();
    }

    private String label(Game game) {
        return game.getAwayTeam() + " at " + game.getHomeTeam();
    }
}
