package com.nickspicks.api.group;

/**
 * A member's standing within one group. Distinct from {@link
 * com.nickspicks.api.user.Role}, which is site-wide: a group owner is not an
 * app admin, and an app admin is not automatically in the group.
 */
public enum GroupRole {
    OWNER,
    MEMBER
}
