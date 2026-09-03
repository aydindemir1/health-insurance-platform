package com.aydindemir.health.authorization.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

public interface CoverageVerificationPort {
    CoverageVerificationResult verify(CoverageVerificationRequest request);

    record CoverageVerificationRequest(
            String policyNumber,
            UUID memberId,
            String serviceCode,
            BigDecimal requestedAmount,
            Currency currency,
            LocalDate serviceDate) {
    }

    record CoverageVerificationResult(boolean eligible, String code, String reason) {
    }
}
