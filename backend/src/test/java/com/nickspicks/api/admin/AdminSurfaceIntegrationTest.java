package com.nickspicks.api.admin;

import com.nickspicks.api.IntegrationTest;
import com.nickspicks.api.group.Cadence;
import com.nickspicks.api.group.Group;
import com.nickspicks.api.group.GroupMember;
import com.nickspicks.api.group.GroupMemberRepository;
import com.nickspicks.api.group.GroupReferral;
import com.nickspicks.api.group.GroupReferralRepository;
import com.nickspicks.api.group.GroupRepository;
import com.nickspicks.api.group.GroupRole;
import com.nickspicks.api.group.TestGroups;
import com.nickspicks.api.user.AppUser;
import com.nickspicks.api.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin member list's group counts, and the searched picker that replaced
 * the load-everyone dropdown.
 */
class AdminSurfaceIntegrationTest extends IntegrationTest {

    private static final UUID ADMIN = UUID.fromString("aaaa1111-0000-0000-0000-000000000001");
    private static final UUID BUILDER = UUID.fromString("bbbb1111-0000-0000-0000-000000000002");
    private static final UUID JOINER = UUID.fromString("cccc1111-0000-0000-0000-000000000003");

    @Autowired
    private AppUserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private GroupMemberRepository members;

    @Autowired
    private GroupReferralRepository referrals;

    @BeforeEach
    void createUsers() {
        users.save(new AppUser(ADMIN, "admin@example.com", "Admin", "admin"));
        users.save(new AppUser(BUILDER, "builder@example.com", "Bea Builder", "builder"));
        users.save(new AppUser(JOINER, "joiner@example.com", "Joe Joiner", "joiner"));
    }

    @Override
    protected void cleanUp() {
        referrals.deleteAll();
        members.deleteAll();
        groups.deleteAll();
        users.deleteAll();
    }

    @Test
    void countsGroupsCreatedJoinedAndReferredPerMember() throws Exception {
        Group one = groups.save(new Group(BUILDER, TestGroups.settings("One", Cadence.WEEKLY, 10)));
        Group two = groups.save(new Group(BUILDER, TestGroups.settings("Two", Cadence.WEEKLY, 10)));
        members.save(new GroupMember(one.getId(), BUILDER, GroupRole.OWNER));
        members.save(new GroupMember(two.getId(), BUILDER, GroupRole.OWNER));
        members.save(new GroupMember(one.getId(), JOINER, GroupRole.MEMBER));

        // Builder's link is what brought the joiner to the site.
        referrals.save(new GroupReferral(JOINER, one.getId(), BUILDER));

        mockMvc.perform(get("/api/admin/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'builder')].groupsCreated").value(2))
                .andExpect(jsonPath("$[?(@.username == 'builder')].groupsJoined").value(2))
                .andExpect(jsonPath("$[?(@.username == 'builder')].referrals").value(1))
                .andExpect(jsonPath("$[?(@.username == 'joiner')].groupsCreated").value(0))
                .andExpect(jsonPath("$[?(@.username == 'joiner')].groupsJoined").value(1))
                .andExpect(jsonPath("$[?(@.username == 'joiner')].referrals").value(0));
    }

    /**
     * A member who created a group and then handed ownership away still shows
     * as having created it - that is a fact about the past, not a role.
     */
    @Test
    void keepsCreditForCreatingAGroupAfterLosingOwnershipOfIt() throws Exception {
        Group group = groups.save(new Group(BUILDER, TestGroups.settings("Handed over",
                Cadence.WEEKLY, 10)));
        members.save(new GroupMember(group.getId(), JOINER, GroupRole.OWNER));

        mockMvc.perform(get("/api/admin/users").with(admin()))
                .andExpect(jsonPath("$[?(@.username == 'builder')].groupsCreated").value(1))
                .andExpect(jsonPath("$[?(@.username == 'builder')].groupsJoined").value(0));
    }

    @Test
    void findsCandidatesByNameUsernameOrEmail() throws Exception {
        Group group = adminGroup();

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates?q=Bea")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("builder"));

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates?q=joiner@")
                        .with(admin()))
                .andExpect(jsonPath("$[0].username").value("joiner"));

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates?q=builder")
                        .with(admin()))
                .andExpect(jsonPath("$[0].displayName").value("Bea Builder"));
    }

    /** Offering to add someone already in is an option that can only fail. */
    @Test
    void leavesExistingMembersOutOfTheCandidates() throws Exception {
        Group group = adminGroup();
        members.save(new GroupMember(group.getId(), BUILDER, GroupRole.MEMBER));

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates?q=builder")
                        .with(admin()))
                .andExpect(jsonPath("$").isEmpty());
    }

    /** No term is the picker's opening state, so it answers rather than refusing. */
    @Test
    void returnsCandidatesWithNoSearchTerm() throws Exception {
        Group group = adminGroup();

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void keepsTheCandidatePickerBehindTheAdminRole() throws Exception {
        Group group = adminGroup();

        mockMvc.perform(get("/api/admin/groups/" + group.getId() + "/candidates")
                        .with(user(JOINER, "joiner")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- fixtures

    /** A group owned by the admin, so only the other two are candidates. */
    private Group adminGroup() {
        Group group = groups.save(new Group(ADMIN, TestGroups.settings("Admin's",
                Cadence.WEEKLY, 10)));
        members.save(new GroupMember(group.getId(), ADMIN, GroupRole.OWNER));
        return group;
    }

    private RequestPostProcessor admin() {
        return user(ADMIN, "admin");
    }

    private RequestPostProcessor user(UUID id, String name) {
        return jwt().jwt(builder -> builder
                .subject(id.toString())
                .claim("email", name + "@example.com")
                .audience(List.of("authenticated")));
    }
}
