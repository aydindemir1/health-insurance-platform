package com.aydindemir.health.policy.application.security;

import java.util.Objects;
import java.util.Set;

public record ActorContext(String subject, Set<ApplicationRole> roles) {
    public ActorContext {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Actor subject must not be blank");
        }
        roles = Set.copyOf(Objects.requireNonNull(roles));
    }

    public boolean hasRole(ApplicationRole role) {
        return roles.contains(role);
    }
}
