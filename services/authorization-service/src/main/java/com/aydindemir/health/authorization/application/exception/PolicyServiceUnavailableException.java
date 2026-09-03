package com.aydindemir.health.authorization.application.exception;

public class PolicyServiceUnavailableException extends RuntimeException {
    public PolicyServiceUnavailableException(String message) {
        super(message);
    }

    public PolicyServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
