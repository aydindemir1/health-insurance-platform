package com.aydindemir.health.claims.application.exception;

public class ClaimsBillingStateConflictException extends RuntimeException {
    public ClaimsBillingStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
