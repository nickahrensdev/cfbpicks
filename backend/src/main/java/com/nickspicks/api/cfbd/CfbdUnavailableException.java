package com.nickspicks.api.cfbd;

/** The upstream data provider could not be reached or is out of quota. */
public class CfbdUnavailableException extends RuntimeException {

    public CfbdUnavailableException(String message) {
        super(message);
    }

    public CfbdUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
