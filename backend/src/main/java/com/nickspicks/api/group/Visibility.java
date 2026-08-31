package com.nickspicks.api.group;

/**
 * Whether a group is listed in search. It says nothing about joining - a
 * password is what gates that, and the two are independent.
 */
public enum Visibility {
    PUBLIC,
    PRIVATE
}
