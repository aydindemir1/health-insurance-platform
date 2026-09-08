package com.aydindemir.health.claims.application.query;

import com.aydindemir.health.claims.application.security.ActorContext;

import java.util.UUID;

public record GetClaimByPreAuthorizationQuery(
        ActorContext actor,
        UUID preAuthorizationId) {
}
