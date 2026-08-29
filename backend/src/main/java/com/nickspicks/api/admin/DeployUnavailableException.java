package com.nickspicks.api.admin;

/** The GitHub Actions dispatch call could not be made or was rejected. */
public class DeployUnavailableException extends RuntimeException {

    public DeployUnavailableException(String message) {
        super(message);
    }

    public DeployUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
