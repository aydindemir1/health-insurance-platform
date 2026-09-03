package com.aydindemir.health.claims.application.mapper;

import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;
import com.aydindemir.health.claims.application.dto.PaymentResult;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.model.Invoice;

public final class ClaimsBillingResultMapper {
    private ClaimsBillingResultMapper() {
    }

    public static ClaimResult toResult(Claim claim) {
        return new ClaimResult(
                claim.id(), claim.preAuthorizationId(), claim.memberId(), claim.providerId(),
                claim.policyNumber(), claim.serviceCode(), claim.claimedAmount().amount(),
                claim.approvedAmount() == null ? null : claim.approvedAmount().amount(),
                claim.claimedAmount().currency().getCurrencyCode(), claim.status().name(),
                claim.rejectionReason(), claim.submittedAt(), claim.reviewStartedAt(), claim.decidedAt());
    }

    public static InvoiceResult toResult(Invoice invoice) {
        var payments = invoice.payments().stream()
                .map(payment -> new PaymentResult(
                        payment.reference(), payment.amount().amount(),
                        payment.amount().currency().getCurrencyCode(), payment.paidAt()))
                .toList();
        return new InvoiceResult(
                invoice.id(), invoice.claimId(), invoice.providerId(), invoice.invoiceNumber(),
                invoice.totalAmount().amount(),
                invoice.payableAmount() == null ? null : invoice.payableAmount().amount(),
                invoice.paidAmount().amount(), invoice.totalAmount().currency().getCurrencyCode(),
                invoice.status().name(), payments, invoice.issuedAt(), invoice.reconciledAt(),
                invoice.settledAt());
    }
}
