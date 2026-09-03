package com.aydindemir.health.claims.application.exception;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(UUID id) {
        super("Claim was not found: " + id);
    }
}
