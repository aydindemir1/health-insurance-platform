package com.aydindemir.health.search.application.dto;

import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchRebuildRecord(
        String id, RecordType type, UUID sourceId, UUID preAuthorizationId,
        UUID memberId, UUID providerId, String policyNumber, String serviceCode,
        String status, String invoiceStatus, String invoiceNumber,
        BigDecimal amount, BigDecimal approvedAmount, BigDecimal paidAmount,
        String currency, String reason, long sourceRevision, Instant occurredAt) {

    public SearchRecord toDomain() {
        return new SearchRecord(
                id, type, sourceId, preAuthorizationId, memberId, providerId,
                policyNumber, serviceCode, status, invoiceStatus, invoiceNumber,
                amount, approvedAmount, paidAmount, currency, reason, sourceRevision, occurredAt);
    }
}
