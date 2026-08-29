package com.nickspicks.api.web;

/** Authenticated, but not allowed to do this. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
