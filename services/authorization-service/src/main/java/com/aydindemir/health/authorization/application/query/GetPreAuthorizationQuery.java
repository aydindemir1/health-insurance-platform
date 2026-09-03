package com.aydindemir.health.authorization.application.query;

import com.aydindemir.health.authorization.application.security.ActorContext;

import java.util.UUID;

public record GetPreAuthorizationQuery(UUID id, ActorContext actor) {
}
