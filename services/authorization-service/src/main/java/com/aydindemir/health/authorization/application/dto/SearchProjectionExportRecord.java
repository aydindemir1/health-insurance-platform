package com.aydindemir.health.authorization.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchProjectionExportRecord(
        String id, String type, UUID sourceId, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        String status, BigDecimal amount, BigDecimal approvedAmount,
        String currency, String reason, long sourceRevision, Instant occurredAt) {
}
