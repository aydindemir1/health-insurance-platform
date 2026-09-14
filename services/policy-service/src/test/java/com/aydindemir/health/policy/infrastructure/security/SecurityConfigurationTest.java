package com.aydindemir.health.policy.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {
    @Test
    void mapsKeycloakRealmRolesToSpringAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("specialist")
                .claim("realm_access", Map.of(
                        "roles", List.of("INSURANCE_SPECIALIST", "offline_access")))
                .build();

        var authentication = new SecurityConfiguration()
                .keycloakRolesConverter()
                .convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("specialist");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_INSURANCE_SPECIALIST", "ROLE_offline_access");
    }
}
