package com.aydindemir.health.authorization.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {
    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void mapsKeycloakRealmRolesToSpringAuthorities() {
        var jwt = jwt(Map.of("roles", List.of("HOSPITAL_USER", "offline_access")));

        var authentication = configuration.keycloakRolesConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_HOSPITAL_USER", "ROLE_offline_access");
    }

    @Test
    void ignoresMalformedRolesClaim() {
        var jwt = jwt(Map.of("roles", "HOSPITAL_USER"));

        var authentication = configuration.keycloakRolesConverter().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("realm_access", realmAccess)
                .build();
    }
}
