package com.aydindemir.health.claims.infrastructure.external;

import com.aydindemir.health.claims.application.exception.AuthorizationServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestApprovedPreAuthorizationAdapterTest {
    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private MockRestServiceServer server;
    private RestApprovedPreAuthorizationAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://authorization-service:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new RestApprovedPreAuthorizationAdapter(builder.build());
        var jwt = new Jwt("access-token", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
                Map.of("alg", "none"), Map.of("sub", "hospital-user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void relaysTokenAndMapsAuthorizationSnapshot() {
        server.expect(once(), requestTo("http://authorization-service:8081/api/v1/pre-authorizations/" + ID))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {"id":"10000000-0000-0000-0000-000000000001",
                         "memberId":"20000000-0000-0000-0000-000000000001",
                         "providerId":"30000000-0000-0000-0000-000000000001",
                         "policyNumber":"POL-100","serviceCode":"IMG-MRI",
                         "requestedAmount":1250.00,"currency":"TRY","status":"APPROVED"}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.findById(ID);

        assertThat(result).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.status()).isEqualTo("APPROVED");
            assertThat(snapshot.authorizedAmount()).isEqualByComparingTo("1250.00");
        });
        server.verify();
    }

    @Test
    void mapsNotFoundWithoutHidingServiceFailures() {
        server.expect(once(), requestTo("http://authorization-service:8081/api/v1/pre-authorizations/" + ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(adapter.findById(ID)).isEmpty();
    }

    @Test
    void failsClosedOnUpstreamFailure() {
        server.expect(once(), requestTo("http://authorization-service:8081/api/v1/pre-authorizations/" + ID))
                .andRespond(withServerError());
        assertThatThrownBy(() -> adapter.findById(ID))
                .isInstanceOf(AuthorizationServiceUnavailableException.class)
                .hasMessageContaining("claim was not created");
    }
}
