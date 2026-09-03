package com.aydindemir.health.claims.application.exception;

public class ConcurrentClaimsBillingUpdateException extends RuntimeException {
    public ConcurrentClaimsBillingUpdateException(String aggregateType, Throwable cause) {
        super(aggregateType + " was changed by another transaction; reload and retry", cause);
    }
}
