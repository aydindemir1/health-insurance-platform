package com.aydindemir.health.authorization.infrastructure.external;

import com.aydindemir.health.authorization.application.exception.PolicyServiceUnavailableException;
import com.aydindemir.health.authorization.application.port.out.CoverageVerificationPort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
class RestCoverageVerificationAdapter implements CoverageVerificationPort {
    private final RestClient restClient;

    RestCoverageVerificationAdapter(RestClient policyServiceRestClient) {
        this.restClient = policyServiceRestClient;
    }

    @Override
    public CoverageVerificationResult verify(CoverageVerificationRequest request) {
        try {
            var response = restClient.post()
                    .uri("/api/v1/coverage-evaluations")
                    .headers(headers -> headers.setBearerAuth(currentAccessToken()))
                    .body(new CoverageEvaluationRequest(
                            request.policyNumber(), request.memberId(), request.serviceCode(),
                            request.requestedAmount(), request.currency().getCurrencyCode(),
                            request.serviceDate()))
                    .retrieve()
                    .body(CoverageEvaluationResponse.class);
            if (response == null) {
                throw new PolicyServiceUnavailableException(
                        "Policy Service returned an empty coverage response");
            }
            return new CoverageVerificationResult(
                    response.eligible(), response.code(), response.reason());
        } catch (ResourceAccessException | RestClientResponseException exception) {
            throw new PolicyServiceUnavailableException(
                    "Policy Service is unavailable; pre-authorization was not created", exception);
        }
    }

    private String currentAccessToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new PolicyServiceUnavailableException(
                "Authenticated bearer token is unavailable for policy evaluation");
    }

    private record CoverageEvaluationRequest(
            String policyNumber,
            UUID memberId,
            String serviceCode,
            BigDecimal requestedAmount,
            String currency,
            LocalDate serviceDate) {
    }

    private record CoverageEvaluationResponse(
            boolean eligible,
            String code,
            String reason) {
    }
}
