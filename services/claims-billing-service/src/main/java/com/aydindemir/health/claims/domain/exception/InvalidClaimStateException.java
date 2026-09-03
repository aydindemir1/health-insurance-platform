package com.aydindemir.health.claims.domain.exception;

public class InvalidClaimStateException extends RuntimeException {
    public InvalidClaimStateException(String message) {
        super(message);
    }
}
