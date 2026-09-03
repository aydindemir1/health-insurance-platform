package com.aydindemir.health.claims.application.query;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.util.Objects;
import java.util.UUID;

public record GetClaimQuery(ActorContext actor, UUID claimId) {
    public GetClaimQuery { Objects.requireNonNull(actor); Objects.requireNonNull(claimId); }
}
