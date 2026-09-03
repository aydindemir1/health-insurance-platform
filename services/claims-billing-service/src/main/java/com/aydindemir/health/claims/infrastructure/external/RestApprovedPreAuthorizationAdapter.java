package com.aydindemir.health.claims.infrastructure.external;

import com.aydindemir.health.claims.application.exception.AuthorizationServiceUnavailableException;
import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Component
class RestApprovedPreAuthorizationAdapter implements ApprovedPreAuthorizationPort {
    private final RestClient restClient;

    RestApprovedPreAuthorizationAdapter(RestClient authorizationServiceRestClient) {
        this.restClient = authorizationServiceRestClient;
    }

    @Override
    public Optional<PreAuthorizationSnapshot> findById(UUID id) {
        try {
            var response = restClient.get()
                    .uri("/api/v1/pre-authorizations/{id}", id)
                    .headers(headers -> headers.setBearerAuth(currentAccessToken()))
                    .retrieve()
                    .body(PreAuthorizationResponse.class);
            if (response == null) {
                throw new AuthorizationServiceUnavailableException(
                        "Authorization Service returned an empty response");
            }
            return Optional.of(new PreAuthorizationSnapshot(
                    response.id(), response.memberId(), response.providerId(),
                    response.policyNumber(), response.serviceCode(), response.requestedAmount(),
                    Currency.getInstance(response.currency()), response.status()));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw unavailable(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        }
    }

    private AuthorizationServiceUnavailableException unavailable(Exception cause) {
        return new AuthorizationServiceUnavailableException(
                "Authorization Service is unavailable; claim was not created", cause);
    }

    private String currentAccessToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new AuthorizationServiceUnavailableException(
                "Authenticated bearer token is unavailable for authorization verification");
    }

    private record PreAuthorizationResponse(
            UUID id, UUID memberId, UUID providerId, String policyNumber,
            String serviceCode, BigDecimal requestedAmount, String currency, String status) {
    }
}
