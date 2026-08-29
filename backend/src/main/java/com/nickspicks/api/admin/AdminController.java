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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public AdminController(CurrentUserService currentUser, AppUserRepository users,
                           PickRepository picks, PickAuditRepository audits,
                           GameRepository games) {
        this.currentUser = currentUser;
        this.users = users;
        this.picks = picks;
        this.audits = audits;
        this.games = games;
    }

    // ----------------------------------------------------------------- users

    public record UserRow(UUID id, String displayName, String email, Role role,
                          long totalPicks, Instant createdAt) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    @GetMapping("/users")
    public List<UserRow> listUsers(@AuthenticationPrincipal Jwt jwt) {
        currentUser.requireAdmin(jwt);

        Map<UUID, Long> pickCounts = picks.countByUser().stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return users.findAll().stream()
                .map(user -> new UserRow(user.getId(), user.getDisplayName(), user.getEmail(),
                        user.getRole(), pickCounts.getOrDefault(user.getId(), 0L),
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

        return new UserRow(target.getId(), target.getDisplayName(), target.getEmail(),
                target.getRole(), 0L, target.getCreatedAt());
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

        Map<UUID, String> names = users.findAll().stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));

        Map<Long, String> gameLabels = new HashMap<>();
        games.findAllById(rows.stream().map(PickAudit::getGameId).distinct().toList())
                .forEach(game -> gameLabels.put(game.getId(), label(game)));

        return rows.stream()
                .map(row -> new ActivityRow(
                        row.getId(),
                        row.getCreatedAt(),
                        row.getUserId(),
                        names.getOrDefault(row.getUserId(), "deleted member"),
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
