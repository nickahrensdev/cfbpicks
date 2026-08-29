package com.nickspicks.api.ingest;

/** A regrade was requested on a game with no final score to grade against. */
public class GameNotGradableException extends RuntimeException {

    public GameNotGradableException(String message) {
        super(message);
    }
}
