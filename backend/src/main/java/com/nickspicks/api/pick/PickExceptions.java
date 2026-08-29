package com.nickspicks.api.pick;

/** Domain failures that map to specific HTTP responses. */
public final class PickExceptions {

    private PickExceptions() {
    }

    /** Too late - the game locks 30 minutes before kickoff. */
    public static class PickWindowClosedException extends RuntimeException {
        public PickWindowClosedException(String message) {
            super(message);
        }
    }

    /** Already holding the weekly maximum. */
    public static class WeeklyLimitReachedException extends RuntimeException {
        public WeeklyLimitReachedException(String message) {
            super(message);
        }
    }

    /** Duplicate pick on a game, or some other invalid request. */
    public static class InvalidPickException extends RuntimeException {
        public InvalidPickException(String message) {
            super(message);
        }
    }

    /**
     * The line moved between the page loading and the pick arriving.
     *
     * <p>Carries the current spread so the UI can show what changed and let
     * the member decide, rather than silently committing them to a number
     * they never saw.
     */
    public static class LineMovedException extends RuntimeException {

        private final java.math.BigDecimal currentLine;

        public LineMovedException(String message, java.math.BigDecimal currentLine) {
            super(message);
            this.currentLine = currentLine;
        }

        /** The market's real number now - a spread or a total. */
        public java.math.BigDecimal getCurrentLine() {
            return currentLine;
        }
    }
}
