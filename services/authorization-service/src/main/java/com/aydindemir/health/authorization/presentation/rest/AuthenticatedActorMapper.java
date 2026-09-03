package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
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
                .map(this::toKnownRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new ActorContext(
                authentication.getName(), providerId(authentication), roles);
    }

    private UUID providerId(JwtAuthenticationToken authentication) {
        String claim = authentication.getToken().getClaimAsString("provider_id");
        if (claim == null || claim.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(claim);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationAccessDeniedException(
                    "The provider_id token claim must be a UUID");
        }
    }

    private ApplicationRole toKnownRole(String role) {
        try {
            return ApplicationRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
