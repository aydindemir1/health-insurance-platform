package com.aydindemir.health.claims.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResult(
        UUID id,
        UUID claimId,
        UUID providerId,
        String invoiceNumber,
        BigDecimal totalAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        String currency,
        String status,
        List<PaymentResult> payments,
        Instant issuedAt,
        Instant reconciledAt,
        Instant settledAt) {
}
