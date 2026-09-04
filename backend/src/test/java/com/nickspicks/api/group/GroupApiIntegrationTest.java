package com.nickspicks.api.group;

import com.jayway.jsonpath.JsonPath;
import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group creation, settings, search and membership end to end.
 * admin@example.com is in app.admin-emails for the test profile, so that
 * account is promoted on first request.
 */
class GroupApiIntegrationTest extends IntegrationTest {

    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000011");
    private static final UUID MEMBER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000012");
    private static final UUID OTHER = UUID.fromString("cccccccc-0000-0000-0000-000000000013");

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    @Autowired
    private AppUserRepository users;

    @Override
    protected void cleanUp() {
        groupMembers.deleteAll();
        groups.deleteAll();
        users.deleteAll();
    }

    // -------------------------------------------------------------- creation

    @Test
    void membersCannotCreateOrListGroups() throws Exception {
        mockMvc.perform(post("/api/admin/groups").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickemJson("The Office", "PUBLIC", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/groups").with(member()))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAGroupEnrolsTheCreatorAsItsOwner() throws Exception {
        String id = create(pickemJson("The Office", "PUBLIC", null));

        mockMvc.perform(get("/api/groups/" + id + "/members").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ADMIN.toString()))
                .andExpect(jsonPath("$[0].role").value("OWNER"));

        // Two now: the league just created, and the personal board every
        // account is given. The list is sorted by name, so "My Board" is
        // first and "The Office" second.
        mockMvc.perform(get("/api/groups/mine").with(admin()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value(PersonalGroups.NAME))
                .andExpect(jsonPath("$[0].personal").value(true))
                // Nothing to configure on a personal board, even for its owner.
                .andExpect(jsonPath("$[0].manageable").value(false))
                .andExpect(jsonPath("$[1].name").value("The Office"))
                .andExpect(jsonPath("$[1].personal").value(false))
                .andExpect(jsonPath("$[1].manageable").value(true))
                .andExpect(jsonPath("$[1].myRole").value("OWNER"))
                .andExpect(jsonPath("$[1].memberCount").value(1));
    }

    @Test
    void settingsRoundTripThroughAnEdit() throws Exception {
        String id = create(pickemJson("The Office", "PUBLIC", "hunter2"));

        mockMvc.perform(put("/api/admin/groups/" + id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickemJson("Renamed", "PRIVATE", "hunter2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.name").value("Renamed"))
                .andExpect(jsonPath("$.settings.visibility").value("PRIVATE"))
                // Readable back only because the caller manages the group.
                .andExpect(jsonPath("$.settings.joinPassword").value("hunter2"))
                .andExpect(jsonPath("$.settings.spreadWinPoints").value(1.0))
                .andExpect(jsonPath("$.settings.spreadPushPoints").value(0.5));
    }

    /**
     * The two types score and cap differently, so switching an established
     * league would re-interpret picks made under the other set of rules.
     */
    @Test
    void theGroupTypeCannotBeChangedAfterCreation() throws Exception {
        String id = create(pickemJson("The Office", "PUBLIC", null));

        mockMvc.perform(put("/api/admin/groups/" + id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "The Office", "visibility": "PUBLIC",
                                 "groupType": "ELIMINATION",
                                 "cadence": "WEEKLY", "lengthType": "PER_YEAR", "startSeason": 2026,
                                 "lockLeadMinutes": 30, "minPicksPerCadence": 1,
                                 "strikesAllowed": 2, "multiplePicksPerGame": true,
                                 "moneylineEnabled": false, "spreadEnabled": true, "totalEnabled": true,
                                 %s}
                                """.formatted(POINTS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_SETTINGS"));

        // Everything else about the group is still editable.
        mockMvc.perform(put("/api/admin/groups/" + id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickemJson("Renamed", "PUBLIC", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.name").value("Renamed"))
                .andExpect(jsonPath("$.settings.groupType").value("PICKEM"));
    }

    /**
     * Strikes are the elimination-only setting. The minimum is not: an unmet
     * one is charged as losses, which a pickem group can carry perfectly well.
     */
    @Test
    void dropsTheStrikeCountOnAPickemGroupButKeepsTheMinimum() throws Exception {
        String id = create("""
                {"name": "Pickem", "visibility": "PUBLIC", "groupType": "PICKEM",
                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                 "lockLeadMinutes": 30, "minPicksPerCadence": 4, "strikesAllowed": 9,
                 "multiplePicksPerGame": true,
                 "moneylineEnabled": true, "spreadEnabled": true, "totalEnabled": true,
                 %s}
                """.formatted(POINTS));

        mockMvc.perform(get("/api/admin/groups/" + id).with(admin()))
                .andExpect(jsonPath("$.settings.strikesAllowed").doesNotExist())
                .andExpect(jsonPath("$.settings.minPicksPerCadence").value(4));
    }

    @Test
    void invalidSettingCombinationsAreRejectedWithAReadableCode() throws Exception {
        // Elimination has to reset each year.
        mockMvc.perform(post("/api/admin/groups").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Survivor", "visibility": "PUBLIC", "groupType": "ELIMINATION",
                                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                                 "lockLeadMinutes": 30, "minPicksPerCadence": 1, "strikesAllowed": 2,
                                 "multiplePicksPerGame": true,
                                 "moneylineEnabled": true, "spreadEnabled": false, "totalEnabled": false,
                                 %s}
                                """.formatted(POINTS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_SETTINGS"));

        // No pick option enabled.
        mockMvc.perform(post("/api/admin/groups").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nothing", "visibility": "PUBLIC", "groupType": "PICKEM",
                                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                                 "lockLeadMinutes": 30, "minPicksPerCadence": 0,
                                 "multiplePicksPerGame": true,
                                 "moneylineEnabled": false, "spreadEnabled": false, "totalEnabled": false,
                                 %s}
                                """.formatted(POINTS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_SETTINGS"));

        assertThat(leagues()).isEmpty();
    }

    @Test
    void missingRequiredFieldsComeBackAsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/groups").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "visibility": "PUBLIC", "groupType": "PICKEM",
                                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                                 "lockLeadMinutes": 30, "minPicksPerCadence": 0,
                                 "multiplePicksPerGame": true,
                                 "moneylineEnabled": true, "spreadEnabled": true, "totalEnabled": true,
                                 %s}
                                """.formatted(POINTS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---------------------------------------------------------------- search

    @Test
    void searchReturnsPublicGroupsAndHidesPrivateOnes() throws Exception {
        create(pickemJson("Open League", "PUBLIC", null));
        create(pickemJson("Locked League", "PUBLIC", "hunter2"));
        create(pickemJson("Secret League", "PRIVATE", null));

        mockMvc.perform(get("/api/groups/search").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Locked League"))
                .andExpect(jsonPath("$[0].passwordRequired").value(true))
                // The password itself is never in a search result.
                .andExpect(jsonPath("$[0].joinPassword").doesNotExist())
                .andExpect(jsonPath("$[0].creatorName").exists())
                .andExpect(jsonPath("$[1].name").value("Open League"))
                .andExpect(jsonPath("$[1].passwordRequired").value(false));

        mockMvc.perform(get("/api/groups/search?q=open").with(member()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Open League"));
    }

    @Test
    void searchMarksGroupsTheCallerIsAlreadyIn() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(get("/api/groups/search").with(member()))
                .andExpect(jsonPath("$[0].alreadyMember").value(true))
                .andExpect(jsonPath("$[0].memberCount").value(2));
    }

    // ------------------------------------------------------------------ join

    @Test
    void joiningAnOpenGroupNeedsNoPassword() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));

        // No approval needed, so they are in immediately and the detail comes
        // back with the result rather than being withheld.
        join(id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.group.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.group.manageable").value(false))
                // A plain member does not get the group's password back.
                .andExpect(jsonPath("$.group.settings.joinPassword").doesNotExist());
    }

    @Test
    void joiningALockedGroupRequiresTheRightPassword() throws Exception {
        String id = create(pickemJson("Locked League", "PUBLIC", "hunter2"));

        join(id, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_PASSWORD_REQUIRED"));

        join(id, "wrong")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_PASSWORD_INCORRECT"));

        join(id, "hunter2").andExpect(status().isOk());
        assertThat(groupMembers.countByGroupId(UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void joiningTwiceIsRejected() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));

        join(id, null).andExpect(status().isOk());
        join(id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_A_MEMBER"));
    }

    @Test
    void privateGroupsCannotBeJoinedEvenWithTheirId() throws Exception {
        String id = create(pickemJson("Secret League", "PRIVATE", null));

        join(id, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------ visibility

    @Test
    void nonMembersCannotReadAGroupsSettings() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", "hunter2"));

        mockMvc.perform(get("/api/groups/" + id).with(member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        join(id, "hunter2").andExpect(status().isOk());
        mockMvc.perform(get("/api/groups/" + id).with(member()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ membership

    @Test
    void ownersRemoveMembersAndMembersRemoveThemselves() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());
        mockMvc.perform(post("/api/groups/" + id + "/join").with(other()))
                .andExpect(status().isOk());

        // A member cannot evict someone else.
        mockMvc.perform(delete("/api/groups/" + id + "/members/" + OTHER).with(member()))
                .andExpect(status().isForbidden());

        // But can leave.
        mockMvc.perform(delete("/api/groups/" + id + "/members/" + MEMBER).with(member()))
                .andExpect(status().isNoContent());

        // And the owner can evict.
        mockMvc.perform(delete("/api/groups/" + id + "/members/" + OTHER).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(groupMembers.countByGroupId(UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void theLastOwnerCannotBeRemoved() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));

        mockMvc.perform(delete("/api/groups/" + id + "/members/" + ADMIN).with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("last owner cannot be removed")));
    }

    // ------------------------------------------------------------- co-owners

    /** Ownership is a role several members can hold, not a single seat. */
    @Test
    void anOwnerCanPromoteAnotherMemberToOwner() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        // A plain member cannot hand themselves the keys.
        mockMvc.perform(put("/api/groups/" + id + "/members/" + MEMBER + "/role").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/" + id + "/members/" + MEMBER + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isNoContent());

        // Both hold OWNER at once, and the new one can now manage the group.
        mockMvc.perform(get("/api/groups/" + id + "/members").with(member()))
                .andExpect(jsonPath("$[?(@.role == 'OWNER')].userId").value(
                        org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/api/groups/" + id).with(member()))
                .andExpect(jsonPath("$.manageable").value(true));
    }

    /** With a co-owner in place the original owner is free to step away. */
    @Test
    void anOwnerCanBeDemotedOrRemovedOnceAnotherOwnerExists() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/" + id + "/members/" + MEMBER + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isNoContent());

        // No longer the last owner, so the creator can now be demoted.
        mockMvc.perform(put("/api/groups/" + id + "/members/" + ADMIN + "/role").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"MEMBER\"}"))
                .andExpect(status().isNoContent());

        // And the remaining owner is now the one who is pinned.
        mockMvc.perform(put("/api/groups/" + id + "/members/" + MEMBER + "/role").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("at least one owner")));
    }

    /** The badge follows who made the group, not who currently runs it. */
    @Test
    void theCreatorIsMarkedEvenAfterBeingDemoted() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/" + id + "/members/" + MEMBER + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"OWNER\"}"));
        mockMvc.perform(put("/api/groups/" + id + "/members/" + ADMIN + "/role").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"MEMBER\"}"));

        mockMvc.perform(get("/api/groups/" + id + "/members").with(member()))
                .andExpect(jsonPath("$[?(@.userId == '" + ADMIN + "')].creator").value(true))
                .andExpect(jsonPath("$[?(@.userId == '" + ADMIN + "')].role").value("MEMBER"))
                .andExpect(jsonPath("$[?(@.userId == '" + MEMBER + "')].creator").value(false));
    }

    // ------------------------------------------------------------ favourites

    @Test
    void favouritingPinsAGroupForThatMemberOnly() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(get("/api/groups/mine").with(member()))
                .andExpect(jsonPath("$[0].favorite").value(false));

        mockMvc.perform(put("/api/groups/" + id + "/favorite").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\": true}"))
                .andExpect(status().isNoContent());

        // Index 1: "My Board" sorts before "Open League".
        mockMvc.perform(get("/api/groups/mine").with(member()))
                .andExpect(jsonPath("$[1].name").value("Open League"))
                .andExpect(jsonPath("$[1].favorite").value(true));

        // A favourite is a property of one membership, not of the group.
        mockMvc.perform(get("/api/groups/mine").with(admin()))
                .andExpect(jsonPath("$[0].favorite").value(false));

        mockMvc.perform(put("/api/groups/" + id + "/favorite").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\": false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/groups/mine").with(member()))
                .andExpect(jsonPath("$[0].favorite").value(false));
    }

    @Test
    void aNonMemberCannotFavouriteAGroup() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));

        mockMvc.perform(put("/api/groups/" + id + "/favorite").with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\": true}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------- approval

    @Test
    void joiningAGroupThatRequiresApprovalQueuesARequest() throws Exception {
        String id = create(approvalJson("Vetted League", null));

        join(id, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true))
                // Not a member yet, so the settings are withheld.
                .andExpect(jsonPath("$.group").doesNotExist());

        assertThat(groupMembers.countByGroupId(UUID.fromString(id))).isEqualTo(1);

        mockMvc.perform(get("/api/groups/" + id + "/requests").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(MEMBER.toString()));

        // Asking twice does not queue a second time.
        join(id, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_A_MEMBER"));
    }

    @Test
    void approvingARequestEnrolsThemAndDenyingDoesNot() throws Exception {
        String approved = create(approvalJson("Yes League", null));
        String denied = create(approvalJson("No League", null));

        join(approved, null).andExpect(status().isOk());
        join(denied, null).andExpect(status().isOk());

        mockMvc.perform(post("/api/groups/" + approved + "/requests/" + MEMBER + "/approve")
                        .with(admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/groups/" + denied + "/requests/" + MEMBER + "/deny")
                        .with(admin()))
                .andExpect(status().isNoContent());

        assertThat(groupMembers.countByGroupId(UUID.fromString(approved))).isEqualTo(2);
        assertThat(groupMembers.countByGroupId(UUID.fromString(denied))).isEqualTo(1);

        // Both queues are empty afterwards.
        mockMvc.perform(get("/api/groups/" + approved + "/requests").with(admin()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/groups/" + denied + "/requests").with(admin()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** The password is checked first, so a wrong one never reaches the queue. */
    @Test
    void aWrongPasswordIsRefusedBeforeAnythingIsQueued() throws Exception {
        String id = create(approvalJson("Locked and vetted", "hunter2"));

        join(id, "wrong")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_PASSWORD_INCORRECT"));

        mockMvc.perform(get("/api/groups/" + id + "/requests").with(admin()))
                .andExpect(jsonPath("$.length()").value(0));

        join(id, "hunter2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true));
    }

    @Test
    void onlyOwnersSeeOrDecideRequests() throws Exception {
        String id = create(approvalJson("Vetted League", null));
        join(id, null).andExpect(status().isOk());

        // The requester is not a member, so the queue is not theirs to read.
        mockMvc.perform(get("/api/groups/" + id + "/requests").with(member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/groups/" + id + "/requests/" + MEMBER + "/approve")
                        .with(member()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- delete

    @Test
    void onlyTheOwnerOrAnAdminCanEditOrDelete() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/" + id).with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickemJson("Hijacked", "PUBLIC", null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/groups/" + id).with(member()))
                .andExpect(status().isForbidden());

        assertThat(leagues()).hasSize(1);
    }

    @Test
    void deletingAGroupTakesItsMembershipWithIt() throws Exception {
        String id = create(pickemJson("Open League", "PUBLIC", null));
        join(id, null).andExpect(status().isOk());

        mockMvc.perform(delete("/api/groups/" + id).with(admin()))
                .andExpect(status().isNoContent());

        // Only the league went. Each account keeps its own personal board,
        // and the membership row that goes with it.
        assertThat(leagues()).isEmpty();
        assertThat(groupMembers.findAll()).hasSize(2);
        // The members themselves survive - only the group did not.
        assertThat(users.findAll()).hasSize(2);
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/groups/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/search")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/groups")).andExpect(status().isUnauthorized());
    }

    /**
     * Nobody is put into a group they did not choose.
     *
     * <p>The admin add-member endpoint is gone, not merely unlinked from the
     * page - an endpoint the UI has stopped calling is still an endpoint. The
     * ways in are search-and-join and an invite link, both of which are acts
     * by the person joining.
     */
    @Test
    void thereIsNoWayToAddSomebodyToAGroup() throws Exception {
        String id = create(pickemJson("No Adding", "PUBLIC", null));

        // 405 rather than 404: the path survives for GET, which lists the
        // members. What matters is that POST is no longer handled.
        mockMvc.perform(post("/api/admin/groups/" + id + "/members").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + MEMBER + "\"}"))
                .andExpect(status().isMethodNotAllowed());

        // And the picker that fed it.
        mockMvc.perform(get("/api/admin/groups/" + id + "/candidates").with(admin()))
                .andExpect(status().isNotFound());

        // Removing one is unaffected - an owner can still put someone out.
        assertThat(groupMembers.findAll()).hasSize(2);
    }

    /**
     * Groups that are actually leagues. Every account now also has a personal
     * board, so findAll() no longer answers "the groups this test made".
     */
    private List<Group> leagues() {
        return groups.findAll().stream().filter(group -> !group.isPersonal()).toList();
    }

    // ------------------------------------------------------------------ setup

    private String create(String body) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/admin/groups").with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions join(String id, String password)
            throws Exception {
        var request = post("/api/groups/" + id + "/join").with(member());
        if (password != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\": \"" + password + "\"}");
        }
        return mockMvc.perform(request);
    }

    private static final String POINTS = """
            "moneylineWinPoints": 1, "moneylineLossPoints": 0, "moneylinePushPoints": 0.5,
            "spreadWinPoints": 1, "spreadLossPoints": 0, "spreadPushPoints": 0.5,
            "totalWinPoints": 1, "totalLossPoints": 0, "totalPushPoints": 0.5
            """;

    /** A public pick'em that queues joiners for an owner to approve. */
    private String approvalJson(String name, String password) {
        return """
                {"name": "%s", "visibility": "PUBLIC", %s "groupType": "PICKEM",
                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                 "lockLeadMinutes": 30, "minPicksPerCadence": 0,
                 "multiplePicksPerGame": true, "requireApproval": true,
                 "moneylineEnabled": true, "spreadEnabled": true, "totalEnabled": true,
                 %s}
                """.formatted(name,
                password == null ? "" : "\"joinPassword\": \"" + password + "\",",
                POINTS);
    }

    private String pickemJson(String name, String visibility, String password) {
        return """
                {"name": "%s", "visibility": "%s", %s "groupType": "PICKEM",
                 "cadence": "WEEKLY", "lengthType": "CONTINUOUS", "startSeason": 2026,
                 "lockLeadMinutes": 30, "minPicksPerCadence": 0,
                 "multiplePicksPerGame": true,
                 "moneylineEnabled": true, "spreadEnabled": true, "totalEnabled": true,
                 %s}
                """.formatted(name, visibility,
                password == null ? "" : "\"joinPassword\": \"" + password + "\",",
                POINTS);
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(builder -> builder
                .subject(ADMIN.toString())
                .claim("email", "admin@example.com")
                .audience(List.of("authenticated")));
    }

    private RequestPostProcessor member() {
        return jwt().jwt(builder -> builder
                .subject(MEMBER.toString())
                .claim("email", "member@example.com")
                .audience(List.of("authenticated")));
    }

    private RequestPostProcessor other() {
        return jwt().jwt(builder -> builder
                .subject(OTHER.toString())
                .claim("email", "other@example.com")
                .audience(List.of("authenticated")));
    }
}
