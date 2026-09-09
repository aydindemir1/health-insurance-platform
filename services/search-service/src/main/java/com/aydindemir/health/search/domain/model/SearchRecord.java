package com.aydindemir.health.search.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SearchRecord(
        String id, RecordType type, UUID sourceId, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        String status, String invoiceStatus, String invoiceNumber,
        BigDecimal amount, BigDecimal approvedAmount, BigDecimal paidAmount,
        String currency, String reason, long sourceRevision, Instant occurredAt) {
    public SearchRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        Objects.requireNonNull(type);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(providerId);
        if (policyNumber == null || policyNumber.isBlank()) throw new IllegalArgumentException("policyNumber must not be blank");
        if (serviceCode == null || serviceCode.isBlank()) throw new IllegalArgumentException("serviceCode must not be blank");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status must not be blank");
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (sourceRevision < 1) throw new IllegalArgumentException("sourceRevision must be positive");
        Objects.requireNonNull(occurredAt);
    }
}
