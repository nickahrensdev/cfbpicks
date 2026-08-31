package com.nickspicks.api.group;

/**
 * Where a request to join a group has got to.
 *
 * <p>Decided requests are kept rather than deleted: an owner refusing someone
 * is a thing that happened, and the row is what stops the same person
 * reappearing in the queue every few minutes.
 */
public enum JoinRequestStatus {
    PENDING,
    APPROVED,
    DENIED
}
