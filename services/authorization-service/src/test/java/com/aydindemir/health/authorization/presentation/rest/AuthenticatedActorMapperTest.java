package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedActorMapperTest {
    private final AuthenticatedActorMapper mapper = new AuthenticatedActorMapper();

    @Test
    void mapsTrustedProviderClaimAndKnownRealmRole() {
        UUID providerId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        var authentication = authentication(
                providerId.toString(), "ROLE_HOSPITAL_USER", "ROLE_unknown-client-role");

        var actor = mapper.from(authentication);

        assertThat(actor.subject()).isEqualTo("user-123");
        assertThat(actor.providerId()).isEqualTo(providerId);
        assertThat(actor.roles()).containsExactly(ApplicationRole.HOSPITAL_USER);
    }

    @Test
    void rejectsMalformedProviderClaim() {
        var authentication = authentication("not-a-uuid", "ROLE_HOSPITAL_USER");

        assertThatThrownBy(() -> mapper.from(authentication))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("provider_id");
    }

    private JwtAuthenticationToken authentication(String providerId, String... authorities) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(Instant.parse("2026-09-03T12:00:00Z"))
                .expiresAt(Instant.parse("2026-09-03T13:00:00Z"))
                .claim("provider_id", providerId)
                .build();
        var grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(jwt, grantedAuthorities, jwt.getSubject());
    }
}
