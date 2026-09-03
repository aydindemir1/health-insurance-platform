package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class AuthenticatedActorMapper {
    ActorContext from(JwtAuthenticationToken authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        var roles = Arrays.stream(ApplicationRole.values())
                .filter(role -> authorities.contains("ROLE_" + role.name()))
                .collect(Collectors.toUnmodifiableSet());
        return new ActorContext(authentication.getToken().getSubject(), roles);
    }
}
