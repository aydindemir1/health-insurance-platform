package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ClaimsBillingResponses {
    private ClaimsBillingResponses() {}

    record ClaimInvoice(Claim claim, Invoice invoice) {
        static ClaimInvoice from(ClaimInvoiceResult result) {
            return new ClaimInvoice(Claim.from(result.claim()), Invoice.from(result.invoice()));
        }
    }

    record Claim(
            UUID id, UUID preAuthorizationId, UUID memberId, UUID providerId,
            String policyNumber, String serviceCode, BigDecimal claimedAmount,
            BigDecimal approvedAmount, String currency, String status,
            String rejectionReason, Instant submittedAt, Instant reviewStartedAt, Instant decidedAt) {
        static Claim from(ClaimResult value) {
            return new Claim(value.id(), value.preAuthorizationId(), value.memberId(),
                    value.providerId(), value.policyNumber(), value.serviceCode(),
                    value.claimedAmount(), value.approvedAmount(), value.currency(), value.status(),
                    value.rejectionReason(), value.submittedAt(), value.reviewStartedAt(), value.decidedAt());
        }
    }

    record Invoice(
            UUID id, UUID claimId, UUID providerId, String invoiceNumber,
            BigDecimal totalAmount, BigDecimal payableAmount, BigDecimal paidAmount,
            String currency, String status, List<Payment> payments,
            Instant issuedAt, Instant reconciledAt, Instant settledAt) {
        static Invoice from(InvoiceResult value) {
            var payments = value.payments().stream()
                    .map(payment -> new Payment(payment.reference(), payment.amount(), payment.paidAt()))
                    .toList();
            return new Invoice(value.id(), value.claimId(), value.providerId(), value.invoiceNumber(),
                    value.totalAmount(), value.payableAmount(), value.paidAmount(), value.currency(),
                    value.status(), payments, value.issuedAt(), value.reconciledAt(), value.settledAt());
        }
    }

    record Payment(String reference, BigDecimal amount, Instant paidAt) {}
}
