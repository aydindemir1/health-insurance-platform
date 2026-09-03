package com.aydindemir.health.claims.application.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ActorContext(String subject, UUID providerId, Set<ApplicationRole> roles) {
    public ActorContext {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Actor subject must not be blank");
        }
        subject = subject.trim();
        roles = Set.copyOf(Objects.requireNonNull(roles));
    }

    public boolean hasRole(ApplicationRole role) {
        return roles.contains(role);
    }
}
