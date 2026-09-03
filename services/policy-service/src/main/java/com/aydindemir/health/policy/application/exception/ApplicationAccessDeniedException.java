package com.aydindemir.health.policy.application.exception;

public class ApplicationAccessDeniedException extends RuntimeException {
    public ApplicationAccessDeniedException(String message) {
        super(message);
    }
}
