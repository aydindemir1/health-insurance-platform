package com.aydindemir.health.authorization.domain.exception;

public final class InvalidPreAuthorizationStateException extends RuntimeException {
    public InvalidPreAuthorizationStateException(String message) {
        super(message);
    }
}
