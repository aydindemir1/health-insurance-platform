package com.aydindemir.health.search.application.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ActorContext(String subject, UUID providerId, Set<ApplicationRole> roles) {
    public ActorContext {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject must not be blank");
        roles = Set.copyOf(Objects.requireNonNull(roles));
    }
    public boolean hasRole(ApplicationRole role) { return roles.contains(role); }
}
