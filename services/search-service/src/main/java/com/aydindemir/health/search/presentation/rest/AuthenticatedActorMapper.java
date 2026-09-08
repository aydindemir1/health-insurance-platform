package com.aydindemir.health.search.presentation.rest;

import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.search.application.security.ActorContext;
import com.aydindemir.health.search.application.security.ApplicationRole;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class AuthenticatedActorMapper {
    ActorContext from(JwtAuthenticationToken authentication) {
        var roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> knownRole(value.substring(5)))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return new ActorContext(authentication.getName(), providerId(authentication), roles);
    }

    private UUID providerId(JwtAuthenticationToken authentication) {
        String value = authentication.getToken().getClaimAsString("provider_id");
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) {
            throw new ApplicationAccessDeniedException("The provider_id token claim must be a UUID");
        }
    }

    private ApplicationRole knownRole(String value) {
        try { return ApplicationRole.valueOf(value); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
