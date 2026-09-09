package com.aydindemir.health.search.infrastructure.elasticsearch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchIndexDocument(
        String id, String type, UUID sourceId, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        String status, String invoiceStatus, String invoiceNumber,
        BigDecimal amount, BigDecimal approvedAmount, BigDecimal paidAmount,
        String currency, String reason, Long sourceRevision, Instant occurredAt) {
}
