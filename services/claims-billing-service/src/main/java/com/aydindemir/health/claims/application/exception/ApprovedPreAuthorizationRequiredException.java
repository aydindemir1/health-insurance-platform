package com.aydindemir.health.claims.application.exception;

import java.util.UUID;

public class ApprovedPreAuthorizationRequiredException extends RuntimeException {
    public ApprovedPreAuthorizationRequiredException(UUID id) {
        super("An approved pre-authorization is required: " + id);
    }
}
