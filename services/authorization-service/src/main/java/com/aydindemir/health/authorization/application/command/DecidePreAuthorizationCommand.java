package com.aydindemir.health.authorization.application.command;

import com.aydindemir.health.authorization.application.security.ActorContext;

import java.util.UUID;

public record DecidePreAuthorizationCommand(UUID id, String reason, ActorContext actor) {
}
