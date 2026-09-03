package com.aydindemir.health.authorization.infrastructure.external;

import com.aydindemir.health.authorization.application.exception.PolicyServiceUnavailableException;
import com.aydindemir.health.authorization.application.port.out.CoverageVerificationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCoverageVerificationAdapterTest {
    private MockRestServiceServer server;
    private RestCoverageVerificationAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://policy-service:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new RestCoverageVerificationAdapter(builder.build());
        var jwt = new Jwt(
                "access-token", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
                java.util.Map.of("alg", "none"), java.util.Map.of("sub", "hospital-user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void relaysBearerTokenAndMapsEligibleResponse() {
        server.expect(once(), requestTo("http://policy-service:8082/api/v1/coverage-evaluations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {
                          "eligible": true,
                          "code": "ELIGIBLE",
                          "reason": "Coverage is eligible",
                          "remainingAmount": 8750.00,
                          "currency": "TRY"
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.verify(request());

        assertThat(result.eligible()).isTrue();
        assertThat(result.code()).isEqualTo("ELIGIBLE");
        server.verify();
    }

    @Test
    void failsClosedWhenPolicyServiceReturnsAnError() {
        server.expect(once(), requestTo("http://policy-service:8082/api/v1/coverage-evaluations"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.verify(request()))
                .isInstanceOf(PolicyServiceUnavailableException.class)
                .hasMessageContaining("was not created");
        server.verify();
    }

    private CoverageVerificationPort.CoverageVerificationRequest request() {
        return new CoverageVerificationPort.CoverageVerificationRequest(
                "POL-100", UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "IMG-MRI", new BigDecimal("1250.00"), Currency.getInstance("TRY"),
                LocalDate.of(2026, 9, 3));
    }
}
