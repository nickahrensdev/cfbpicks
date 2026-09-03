package com.nickspicks.api.group;

/**
 * Group failures the UI needs to tell apart. Each maps to a machine-readable
 * {@code code} in {@link com.nickspicks.api.web.ApiExceptionHandler}, matching
 * how {@link com.nickspicks.api.pick.PickExceptions} works.
 */
public final class GroupExceptions {

    private GroupExceptions() {
    }

    /** A settings combination the group cannot be saved in. */
    public static class InvalidGroupSettingsException extends RuntimeException {
        public InvalidGroupSettingsException(String message) {
            super(message);
        }
    }

    /** The group is password protected and none was supplied. */
    public static class PasswordRequiredException extends RuntimeException {
        public PasswordRequiredException(String message) {
            super(message);
        }
    }

    /**
     * The group stopped accepting members when it started.
     *
     * <p>Its own type rather than a bare Forbidden so the UI can say which of
     * the several reasons a join can fail actually applied.
     */
    public static class JoinsClosedException extends RuntimeException {
        public JoinsClosedException(String message) {
            super(message);
        }
    }

    /** A password was supplied and it was wrong. */
    public static class PasswordIncorrectException extends RuntimeException {
        public PasswordIncorrectException(String message) {
            super(message);
        }
    }

    /** The member is already in the group. */
    public static class AlreadyMemberException extends RuntimeException {
        public AlreadyMemberException(String message) {
            super(message);
        }
    }
}
