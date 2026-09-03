package com.aydindemir.health.authorization.application;

import java.util.UUID;

public final class PreAuthorizationNotFoundException extends RuntimeException {
    public PreAuthorizationNotFoundException(UUID id) {
        super("Pre-authorization not found: " + id);
    }
}
