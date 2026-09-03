package com.nickspicks.api.group;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Share links: who may make one, what it discloses before sign-in, where it
 * admits, and who gets the credit.
 */
class GroupSharingIntegrationTest extends IntegrationTest {

    private static final UUID OWNER = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("cccc0000-0000-0000-0000-000000000003");

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository members;

    @Autowired
    private GroupJoinRequestRepository joinRequests;

    @Autowired
    private GroupShareLinkRepository shareLinks;

    @Autowired
    private GroupReferralRepository referrals;

    @Autowired
    private AppUserRepository users;

    /**
     * The three accounts exist before any group does - pick_group.created_by
     * is a foreign key, so a group cannot be saved for an owner who is not
     * there yet.
     */
    @org.junit.jupiter.api.BeforeEach
    void createUsers() {
        account(OWNER, "owner");
        account(MEMBER, "member");
        account(OUTSIDER, "outsider");
    }

    private void account(UUID id, String name) {
        users.save(new AppUser(id, name + "@example.com", name, name));
    }

    @Override
    protected void cleanUp() {
        referrals.deleteAll();
        shareLinks.deleteAll();
        joinRequests.deleteAll();
        members.deleteAll();
        groups.deleteAll();
        users.deleteAll();
    }

    /** A link someone has already pasted into a message has to keep working. */
    @Test
    void returnsTheSameLinkEveryTimeTheSameMemberSharesTheSameGroup() throws Exception {
        Group group = publicGroup();
        enrol(group, MEMBER, GroupRole.MEMBER);

        String first = share(group, member());
        String second = share(group, member());

        assertThat(first).isEqualTo(second);
    }

    /** Two members of one group get two links, so the credit can be told apart. */
    @Test
    void givesEachSharerTheirOwnLink() throws Exception {
        Group group = publicGroup();
        enrol(group, MEMBER, GroupRole.MEMBER);

        assertThat(share(group, owner())).isNotEqualTo(share(group, member()));
    }

    @Test
    void refusesToShareAGroupTheCallerIsNotIn() throws Exception {
        Group group = publicGroup();

        mockMvc.perform(post("/api/groups/" + group.getId() + "/share").with(outsider()))
                .andExpect(status().isForbidden());
    }

    /**
     * A private group is private because its owner chose that. Members must not
     * be able to route around the decision by passing links out.
     */
    @Test
    void refusesToShareAPrivateGroupUntilItsOwnerAllowsIt() throws Exception {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PRIVATE, false, null)));
        enrol(group, OWNER, GroupRole.OWNER);
        enrol(group, MEMBER, GroupRole.MEMBER);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/share").with(member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The owner could change the setting anyway, so they are never blocked
        // by it.
        mockMvc.perform(post("/api/groups/" + group.getId() + "/share").with(owner()))
                .andExpect(status().isOk());
    }

    @Test
    void letsMembersShareAPrivateGroupOnceItsOwnerOptsIn() throws Exception {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PRIVATE, true, null)));
        enrol(group, OWNER, GroupRole.OWNER);
        enrol(group, MEMBER, GroupRole.MEMBER);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/share").with(member()))
                .andExpect(status().isOk());
    }

    /**
     * The invitation has to explain itself to someone who has no account yet -
     * that is the moment they are deciding whether to make one.
     */
    @Test
    void showsTheInvitationWithoutAnAccount() throws Exception {
        Group group = publicGroup();
        String token = share(group, owner());

        mockMvc.perform(get("/api/share/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shared League"))
                .andExpect(jsonPath("$.sharerName").value("owner"))
                .andExpect(jsonPath("$.passwordRequired").value(false))
                // Never the password, and never who else is in.
                .andExpect(jsonPath("$.joinPassword").doesNotExist());
    }

    @Test
    void saysWhenTheInvitedGroupNeedsAPassword() throws Exception {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PUBLIC, false, "hunter2")));
        enrol(group, OWNER, GroupRole.OWNER);

        mockMvc.perform(get("/api/share/" + share(group, owner())))
                .andExpect(jsonPath("$.passwordRequired").value(true));
    }

    @Test
    void rejectsATokenThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/share/not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creditsTheSharerWhenTheInviteeClaimsTheLink() throws Exception {
        Group group = publicGroup();
        String token = share(group, owner());

        mockMvc.perform(post("/api/share/" + token + "/claim").with(outsider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andExpect(jsonPath("$.groupId").value(group.getId().toString()));

        assertThat(referrals.findById(OUTSIDER)).get()
                .satisfies(referral -> {
                    assertThat(referral.getSharerId()).isEqualTo(OWNER);
                    assertThat(referral.getGroupId()).isEqualTo(group.getId());
                });
        assertThat(referrals.countBySharerId(OWNER)).isEqualTo(1);
    }

    /**
     * One attribution per person, for ever. Otherwise a member could farm their
     * own count by passing links around people who already have accounts.
     */
    @Test
    void keepsTheFirstAttributionWhenASecondLinkIsClaimed() throws Exception {
        Group first = publicGroup();
        enrol(first, MEMBER, GroupRole.MEMBER);

        mockMvc.perform(post("/api/share/" + share(first, owner()) + "/claim").with(outsider()));
        mockMvc.perform(post("/api/share/" + share(first, member()) + "/claim").with(outsider()));

        assertThat(referrals.findById(OUTSIDER)).get()
                .satisfies(referral -> assertThat(referral.getSharerId()).isEqualTo(OWNER));
        assertThat(referrals.countBySharerId(MEMBER)).isZero();
    }

    @Test
    void neverCreditsSomeoneForTheirOwnLink() throws Exception {
        Group group = publicGroup();
        String token = share(group, owner());

        mockMvc.perform(post("/api/share/" + token + "/claim").with(owner()))
                .andExpect(jsonPath("$.alreadyMember").value(true));

        assertThat(referrals.countBySharerId(OWNER)).isZero();
    }

    @Test
    void joinsThroughTheLink() throws Exception {
        Group group = publicGroup();
        String token = share(group, owner());

        mockMvc.perform(post("/api/share/" + token + "/join").with(outsider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false));

        assertThat(members.existsByGroupIdAndUserId(group.getId(), OUTSIDER)).isTrue();
    }

    /**
     * The one path into a private group that needs no owner to act - the link
     * is itself an act by somebody already inside.
     */
    @Test
    void admitsToAPrivateGroupThatSearchAndJoinWouldRefuse() throws Exception {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PRIVATE, true, null)));
        enrol(group, OWNER, GroupRole.OWNER);
        String token = share(group, owner());

        // The public join path still refuses it, which is what makes the link
        // the deliberate exception rather than a hole.
        mockMvc.perform(post("/api/groups/" + group.getId() + "/join").with(outsider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/share/" + token + "/join").with(outsider()))
                .andExpect(status().isOk());

        assertThat(members.existsByGroupIdAndUserId(group.getId(), OUTSIDER)).isTrue();
    }

    /** A link is an invitation, not a bypass: the password still applies. */
    @Test
    void stillAsksForThePasswordWhenJoiningThroughALink() throws Exception {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PUBLIC, false, "hunter2")));
        enrol(group, OWNER, GroupRole.OWNER);
        String token = share(group, owner());

        mockMvc.perform(post("/api/share/" + token + "/join").with(outsider()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_PASSWORD_REQUIRED"));

        mockMvc.perform(post("/api/share/" + token + "/join").with(outsider())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"hunter2\"}"))
                .andExpect(status().isOk());
    }

    /** Nor a bypass of the approval queue. */
    @Test
    void stillQueuesForApprovalWhenJoiningThroughALink() throws Exception {
        Group group = groups.save(new Group(OWNER, approvalSettings()));
        enrol(group, OWNER, GroupRole.OWNER);

        mockMvc.perform(post("/api/share/" + share(group, owner()) + "/join").with(outsider()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true));

        assertThat(members.existsByGroupIdAndUserId(group.getId(), OUTSIDER)).isFalse();
    }

    // ------------------------------------------------------------- fixtures

    private String share(Group group, RequestPostProcessor as) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/groups/" + group.getId() + "/share").with(as))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private Group publicGroup() {
        Group group = groups.save(new Group(OWNER, settings(Visibility.PUBLIC, false, null)));
        enrol(group, OWNER, GroupRole.OWNER);
        return group;
    }

    private void enrol(Group group, UUID userId, GroupRole role) {
        members.save(new GroupMember(group.getId(), userId, role));
    }

    private GroupSettings settings(Visibility visibility, boolean shareable, String password) {
        return build(visibility, shareable, password, false);
    }

    private GroupSettings approvalSettings() {
        return build(Visibility.PUBLIC, false, null, true);
    }

    private GroupSettings build(Visibility visibility, boolean shareable, String password,
                                boolean requireApproval) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal half = new BigDecimal("0.5");

        return new GroupSettings(
                "Shared League", null, visibility, password,
                GroupType.PICKEM, Cadence.WEEKLY, LengthType.CONTINUOUS, 2026,
                30, 10, 0, true, requireApproval, shareable,
                false, true, true,
                null, null, null, null, null, null,
                one, zero, half,
                one, zero, half,
                one, zero, half,
                null, null, null,
                java.time.LocalDate.now(), false);
    }

    private RequestPostProcessor owner() {
        return user(OWNER, "owner");
    }

    private RequestPostProcessor member() {
        return user(MEMBER, "member");
    }

    private RequestPostProcessor outsider() {
        return user(OUTSIDER, "outsider");
    }

    private RequestPostProcessor user(UUID id, String name) {
        return jwt().jwt(builder -> builder
                .subject(id.toString())
                .claim("email", name + "@example.com")
                .audience(List.of("authenticated")));
    }
}
