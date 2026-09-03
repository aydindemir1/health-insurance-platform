package com.aydindemir.health.claims.application.command;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.util.Objects;
import java.util.UUID;

public record ClaimActionCommand(ActorContext actor, UUID claimId, String reason) {
    public ClaimActionCommand {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(claimId);
    }
}
