package com.nickspicks.api.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An isolated picking league and every rule it plays by.
 *
 * <p>The table is {@code pick_group} because {@code group} is a SQL reserved
 * word and the schema is read by raw SQL in places.
 *
 * <p>Settings are typed columns rather than a JSON blob so Postgres can reject
 * a nonsensical combination even if something writes to the table directly.
 * The cross-field rules are duplicated in {@link GroupSettings#validate()},
 * which exists to produce a readable message; the check constraints are the
 * backstop.
 *
 * <p>Like every other entity here there are no JPA associations - {@code
 * ownerId} is a bare column and joins are explicit.
 */
@Entity
@Table(name = "pick_group")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    /**
     * Stored as entered so owners can read it back and share it. Never
     * serialised - responses carry only whether one is set.
     */
    @Column(name = "join_password")
    private String joinPassword;

    /**
     * Who made the group. Identity only - authority lives in
     * {@link GroupMember#getRole()}, so a group can have several owners and
     * the creator need not still be one of them.
     *
     * <p>Nullable: the group outlives its creator's account.
     */
    @Column(name = "created_by")
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false)
    private GroupType groupType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cadence cadence;

    @Enumerated(EnumType.STRING)
    @Column(name = "length_type", nullable = false)
    private LengthType lengthType;

    @Column(name = "start_season", nullable = false)
    private Integer startSeason;

    @Column(name = "lock_lead_minutes", nullable = false)
    private int lockLeadMinutes = 30;

    /** Null means no cap. */
    @Column(name = "max_picks_per_cadence")
    private Integer maxPicksPerCadence;

    @Column(name = "min_picks_per_cadence", nullable = false)
    private int minPicksPerCadence = 1;

    @Column(name = "multiple_picks_per_game", nullable = false)
    private boolean multiplePicksPerGame = true;

    /** When set, joining produces a request for an owner to approve. */
    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval;

    /**
     * Whether an ordinary member may share a private group.
     *
     * <p>Only meaningful when the group is private: a public group is already
     * findable by search, so there is nothing for this to protect. A private
     * group is private because its owner chose that, and members must not be
     * able to route around the decision by passing links out.
     */
    @Column(name = "shareable_by_members", nullable = false)
    private boolean shareableByMembers;

    /**
     * The first game day this group counts, in the zone the schedule is
     * bucketed by - see CadencePeriod. Settlement ignores periods that closed
     * before it, which is what lets a group start mid-season.
     */
    @Column(name = "starts_on", nullable = false)
    private java.time.LocalDate startsOn;

    /** Joining is refused from {@link #startsOn} onward. */
    @Column(name = "joins_close_at_start", nullable = false)
    private boolean joinsCloseAtStart;

    /**
     * A private board of one, created with the account - see
     * {@link PersonalGroups}.
     *
     * <p>Not part of {@link GroupSettings}, deliberately: settings are what a
     * caller may send, and nothing a caller sends should be able to turn a
     * league into a personal board or the other way round. It is set once, at
     * creation, and {@link #apply} never touches it.
     */
    @Column(nullable = false)
    private boolean personal;

    @Column(name = "moneyline_enabled", nullable = false)
    private boolean moneylineEnabled;

    @Column(name = "spread_enabled", nullable = false)
    private boolean spreadEnabled;

    @Column(name = "total_enabled", nullable = false)
    private boolean totalEnabled;

    /**
     * Per-market limits on how many picks one member may hold in a period.
     *
     * <p>Null means no limit on either half. The maximums are checked when a
     * pick is made; the minimums cannot be, because a member part-way through
     * a period has not broken one yet - those are judged when the period
     * closes.
     */
    @Column(name = "moneyline_min_per_cadence")
    private Integer moneylineMinPerCadence;

    @Column(name = "moneyline_max_per_cadence")
    private Integer moneylineMaxPerCadence;

    @Column(name = "spread_min_per_cadence")
    private Integer spreadMinPerCadence;

    @Column(name = "spread_max_per_cadence")
    private Integer spreadMaxPerCadence;

    @Column(name = "total_min_per_cadence")
    private Integer totalMinPerCadence;

    @Column(name = "total_max_per_cadence")
    private Integer totalMaxPerCadence;

    @Column(name = "moneyline_win_points", nullable = false)
    private BigDecimal moneylineWinPoints;

    @Column(name = "moneyline_loss_points", nullable = false)
    private BigDecimal moneylineLossPoints;

    @Column(name = "moneyline_push_points", nullable = false)
    private BigDecimal moneylinePushPoints;

    @Column(name = "spread_win_points", nullable = false)
    private BigDecimal spreadWinPoints;

    @Column(name = "spread_loss_points", nullable = false)
    private BigDecimal spreadLossPoints;

    @Column(name = "spread_push_points", nullable = false)
    private BigDecimal spreadPushPoints;

    @Column(name = "total_win_points", nullable = false)
    private BigDecimal totalWinPoints;

    @Column(name = "total_loss_points", nullable = false)
    private BigDecimal totalLossPoints;

    @Column(name = "total_push_points", nullable = false)
    private BigDecimal totalPushPoints;

    /** Elimination only; null for pickem. */
    @Column(name = "strikes_allowed")
    private Integer strikesAllowed;

    /** Null means a team may be picked any number of times. */
    @Column(name = "team_pick_limit")
    private Integer teamPickLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_pick_limit_scope")
    private TeamLimitScope teamPickLimitScope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Group() {
    }

    public Group(UUID createdBy, GroupSettings settings) {
        this(createdBy, settings, false);
    }

    public Group(UUID createdBy, GroupSettings settings, boolean personal) {
        this.createdBy = createdBy;
        this.personal = personal;
        apply(settings);
    }

    /**
     * Copies a validated settings payload onto the row. Create and update take
     * the same path so the two can never diverge in what they accept.
     */
    public void apply(GroupSettings settings) {
        this.name = settings.name().trim();
        this.description = settings.description() == null || settings.description().isBlank()
                ? null
                : settings.description().trim();
        this.visibility = settings.visibility();
        this.joinPassword = settings.joinPassword() == null || settings.joinPassword().isBlank()
                ? null
                : settings.joinPassword();
        this.groupType = settings.groupType();
        this.cadence = settings.cadence();
        this.lengthType = settings.lengthType();
        this.startSeason = settings.startSeason();
        this.lockLeadMinutes = settings.lockLeadMinutes();
        this.maxPicksPerCadence = settings.maxPicksPerCadence();
        this.multiplePicksPerGame = settings.multiplePicksPerGame();
        this.requireApproval = settings.requireApproval();
        // Absent means "starts now". A caller that does not care about the
        // date should not have to compute today's for itself.
        this.startsOn = settings.startsOn() == null
                ? java.time.LocalDate.now(com.nickspicks.api.pick.CadencePeriod.GAME_DAY_ZONE)
                : settings.startsOn();
        this.joinsCloseAtStart = settings.joinsCloseAtStart();
        this.shareableByMembers = settings.shareableByMembers();

        this.moneylineEnabled = settings.moneylineEnabled();
        this.spreadEnabled = settings.spreadEnabled();
        this.totalEnabled = settings.totalEnabled();

        // A limit on a market nobody can play is dead configuration that would
        // reappear if the market were switched back on later.
        this.moneylineMinPerCadence = settings.moneylineEnabled() ? settings.moneylineMinPerCadence() : null;
        this.moneylineMaxPerCadence = settings.moneylineEnabled() ? settings.moneylineMaxPerCadence() : null;
        this.spreadMinPerCadence = settings.spreadEnabled() ? settings.spreadMinPerCadence() : null;
        this.spreadMaxPerCadence = settings.spreadEnabled() ? settings.spreadMaxPerCadence() : null;
        this.totalMinPerCadence = settings.totalEnabled() ? settings.totalMinPerCadence() : null;
        this.totalMaxPerCadence = settings.totalEnabled() ? settings.totalMaxPerCadence() : null;

        this.moneylineWinPoints = settings.moneylineWinPoints();
        this.moneylineLossPoints = settings.moneylineLossPoints();
        this.moneylinePushPoints = settings.moneylinePushPoints();
        this.spreadWinPoints = settings.spreadWinPoints();
        this.spreadLossPoints = settings.spreadLossPoints();
        this.spreadPushPoints = settings.spreadPushPoints();
        this.totalWinPoints = settings.totalWinPoints();
        this.totalLossPoints = settings.totalLossPoints();
        this.totalPushPoints = settings.totalPushPoints();

        this.teamPickLimit = settings.teamPickLimit();
        this.teamPickLimitScope = settings.teamPickLimit() == null
                ? null
                : settings.teamPickLimitScope();

        // A minimum applies to either type. It used to be elimination-only,
        // back when the only thing that could happen to someone who missed it
        // was being knocked out; now an unmet minimum is charged as losses, so
        // a pickem group can hold members to a number of picks too - and it
        // would be odd to allow "at least 3 spreads a week" per market while
        // refusing "at least 3 picks a week" overall.
        this.minPicksPerCadence = settings.minPicksPerCadence();

        // Strikes stay elimination-only: nothing in a pickem group eliminates
        // anyone, and leaving a stale count behind would confuse whoever read
        // the settings next.
        this.strikesAllowed = settings.groupType() == GroupType.ELIMINATION
                ? settings.strikesAllowed()
                : null;

        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getJoinPassword() {
        return joinPassword;
    }

    /** True when joining requires the password. Safe to expose; the value is not. */
    public boolean isPasswordProtected() {
        return joinPassword != null && !joinPassword.isBlank();
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isRequireApproval() {
        return requireApproval;
    }

    public GroupType getGroupType() {
        return groupType;
    }

    public Cadence getCadence() {
        return cadence;
    }

    public LengthType getLengthType() {
        return lengthType;
    }

    public Integer getStartSeason() {
        return startSeason;
    }

    public int getLockLeadMinutes() {
        return lockLeadMinutes;
    }

    /** The most picks this market allows in one period, or null for no limit. */
    public Integer maxFor(com.nickspicks.api.pick.Market market) {
        return switch (market) {
            case MONEYLINE -> moneylineMaxPerCadence;
            case SPREAD -> spreadMaxPerCadence;
            case TOTAL -> totalMaxPerCadence;
        };
    }

    /** The fewest picks this market requires in one period, or null for none. */
    public Integer minFor(com.nickspicks.api.pick.Market market) {
        return switch (market) {
            case MONEYLINE -> moneylineMinPerCadence;
            case SPREAD -> spreadMinPerCadence;
            case TOTAL -> totalMinPerCadence;
        };
    }

    public Integer getMoneylineMinPerCadence() {
        return moneylineMinPerCadence;
    }

    public Integer getMoneylineMaxPerCadence() {
        return moneylineMaxPerCadence;
    }

    public Integer getSpreadMinPerCadence() {
        return spreadMinPerCadence;
    }

    public Integer getSpreadMaxPerCadence() {
        return spreadMaxPerCadence;
    }

    public Integer getTotalMinPerCadence() {
        return totalMinPerCadence;
    }

    public Integer getTotalMaxPerCadence() {
        return totalMaxPerCadence;
    }

    public boolean isShareableByMembers() {
        return shareableByMembers;
    }

    /**
     * Whether this member may hand out a link to the group.
     *
     * <p>A public group is findable anyway, so sharing it adds convenience,
     * not access. A private one needs the owner to have opted in - or the
     * caller to be an owner themselves, who could change the setting regardless.
     */
    public boolean isShareableBy(GroupRole role) {
        // Nobody, including its owner. There is nothing to invite anyone to.
        if (personal) {
            return false;
        }
        if (role == null) {
            return false;
        }
        return visibility == Visibility.PUBLIC
                || shareableByMembers
                || role == GroupRole.OWNER;
    }

    public boolean isPersonal() {
        return personal;
    }

    public java.time.LocalDate getStartsOn() {
        return startsOn;
    }

    public boolean isJoinsCloseAtStart() {
        return joinsCloseAtStart;
    }

    /**
     * Whether this group has begun, as the schedule reckons a day.
     *
     * <p>Compared in the game-day zone rather than the server's, so a group
     * starting "on the 12th" begins when the 12th does for the games, not when
     * it does for whatever machine happens to be asking.
     */
    public boolean hasStarted() {
        return !java.time.LocalDate
                .now(com.nickspicks.api.pick.CadencePeriod.GAME_DAY_ZONE)
                .isBefore(startsOn);
    }

    public Integer getMaxPicksPerCadence() {
        return maxPicksPerCadence;
    }

    public int getMinPicksPerCadence() {
        return minPicksPerCadence;
    }

    public boolean isMultiplePicksPerGame() {
        return multiplePicksPerGame;
    }

    public boolean isMoneylineEnabled() {
        return moneylineEnabled;
    }

    public boolean isSpreadEnabled() {
        return spreadEnabled;
    }

    public boolean isTotalEnabled() {
        return totalEnabled;
    }

    public BigDecimal getMoneylineWinPoints() {
        return moneylineWinPoints;
    }

    public BigDecimal getMoneylineLossPoints() {
        return moneylineLossPoints;
    }

    public BigDecimal getMoneylinePushPoints() {
        return moneylinePushPoints;
    }

    public BigDecimal getSpreadWinPoints() {
        return spreadWinPoints;
    }

    public BigDecimal getSpreadLossPoints() {
        return spreadLossPoints;
    }

    public BigDecimal getSpreadPushPoints() {
        return spreadPushPoints;
    }

    public BigDecimal getTotalWinPoints() {
        return totalWinPoints;
    }

    public BigDecimal getTotalLossPoints() {
        return totalLossPoints;
    }

    public BigDecimal getTotalPushPoints() {
        return totalPushPoints;
    }

    public Integer getStrikesAllowed() {
        return strikesAllowed;
    }

    public Integer getTeamPickLimit() {
        return teamPickLimit;
    }

    public TeamLimitScope getTeamPickLimitScope() {
        return teamPickLimitScope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
