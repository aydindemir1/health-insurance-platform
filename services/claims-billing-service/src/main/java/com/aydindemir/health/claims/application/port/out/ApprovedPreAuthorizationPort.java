package com.aydindemir.health.claims.application.port.out;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
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
    }
}
