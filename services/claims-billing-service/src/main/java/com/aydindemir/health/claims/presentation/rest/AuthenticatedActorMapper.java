package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class AuthenticatedActorMapper {
    ActorContext from(JwtAuthenticationToken authentication) {
        Objects.requireNonNull(authentication);
        Set<ApplicationRole> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(this::knownRole).filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new ActorContext(authentication.getName(), providerId(authentication), roles);
    }

    private UUID providerId(JwtAuthenticationToken authentication) {
        String value = authentication.getToken().getClaimAsString("provider_id");
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationAccessDeniedException("The provider_id token claim must be a UUID");
        }
    }

    private ApplicationRole knownRole(String value) {
        try { return ApplicationRole.valueOf(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
