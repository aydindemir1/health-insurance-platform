package com.aydindemir.health.claims.application.exception;

public class ApplicationAccessDeniedException extends RuntimeException {
    public ApplicationAccessDeniedException(String message) {
        super(message);
    }
}
