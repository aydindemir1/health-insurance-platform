package com.aydindemir.health.authorization.application.exception;

public final class ApplicationAccessDeniedException extends RuntimeException {
    public ApplicationAccessDeniedException(String message) {
        super(message);
    }
}
