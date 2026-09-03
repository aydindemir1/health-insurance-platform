package com.aydindemir.health.policy.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PolicyResult(
        UUID id,
        String policyNumber,
        UUID memberId,
        LocalDate validFrom,
        LocalDate validUntil,
        String status,
        List<CoverageResult> coverages) {
}
