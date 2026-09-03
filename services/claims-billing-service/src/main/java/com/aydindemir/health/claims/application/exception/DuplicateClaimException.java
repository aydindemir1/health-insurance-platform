package com.aydindemir.health.claims.application.exception;

import java.util.UUID;

public class DuplicateClaimException extends RuntimeException {
    public DuplicateClaimException(UUID preAuthorizationId) {
        super("A claim already exists for pre-authorization: " + preAuthorizationId);
    }

    public DuplicateClaimException(UUID preAuthorizationId, Throwable cause) {
        super("A claim already exists for pre-authorization: " + preAuthorizationId, cause);
    }
}
