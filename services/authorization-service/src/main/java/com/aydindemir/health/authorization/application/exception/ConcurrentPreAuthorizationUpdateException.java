package com.aydindemir.health.authorization.application.exception;

import java.util.UUID;

public final class ConcurrentPreAuthorizationUpdateException extends RuntimeException {
    public ConcurrentPreAuthorizationUpdateException(UUID id, Throwable cause) {
        super("Pre-authorization was concurrently updated: " + id, cause);
    }
}
