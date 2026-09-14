package com.aydindemir.health.claims.application.port.out;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface ApprovedPreAuthorizationPort {
    Optional<PreAuthorizationSnapshot> findById(UUID id);

    record PreAuthorizationSnapshot(
            UUID id,
            UUID memberId,
            UUID providerId,
            String policyNumber,
            String serviceCode,
            BigDecimal authorizedAmount,
            Currency currency,
            String status) {
        private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

        public PreAuthorizationSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(memberId, "memberId");
            Objects.requireNonNull(providerId, "providerId");
            policyNumber = requireText(policyNumber, "policyNumber");
            serviceCode = requireText(serviceCode, "serviceCode");
            Objects.requireNonNull(authorizedAmount, "authorizedAmount");
            Objects.requireNonNull(currency, "currency");
            status = requireText(status, "status");
            if (authorizedAmount.signum() <= 0) {
                throw new IllegalArgumentException("authorizedAmount must be positive");
            }
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Unknown pre-authorization status");
            }
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }
}
