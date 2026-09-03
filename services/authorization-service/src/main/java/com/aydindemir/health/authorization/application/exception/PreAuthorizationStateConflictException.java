package com.aydindemir.health.authorization.application.exception;

public final class PreAuthorizationStateConflictException extends RuntimeException {
    public PreAuthorizationStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
