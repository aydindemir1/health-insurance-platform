package com.aydindemir.health.claims.domain.model;

import com.aydindemir.health.claims.domain.exception.InvalidInvoiceStateException;
import com.aydindemir.health.claims.domain.valueobject.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Invoice {
    private final UUID id;
    private final UUID claimId;
    private final UUID providerId;
    private final String invoiceNumber;
    private final Money totalAmount;
    private final Instant issuedAt;
    private final long revision;
    private final List<Payment> payments;
    private InvoiceStatus status;
    private Money payableAmount;
    private Instant reconciledAt;
    private Instant settledAt;

    private Invoice(
            UUID id,
            UUID claimId,
            UUID providerId,
            String invoiceNumber,
            Money totalAmount,
            InvoiceStatus status,
            Money payableAmount,
            List<Payment> payments,
            Instant issuedAt,
            Instant reconciledAt,
            Instant settledAt,
            long revision) {
        this.id = Objects.requireNonNull(id);
        this.claimId = Objects.requireNonNull(claimId);
        this.providerId = Objects.requireNonNull(providerId);
        this.invoiceNumber = requireText(invoiceNumber, "invoiceNumber");
        this.totalAmount = Objects.requireNonNull(totalAmount);
        if (totalAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Invoice total must be positive");
        }
        this.status = Objects.requireNonNull(status);
        this.payableAmount = payableAmount;
        this.payments = new ArrayList<>(Objects.requireNonNull(payments));
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.reconciledAt = reconciledAt;
        this.settledAt = settledAt;
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
        validatePayments();
        validateLifecycle();
    }

    public static Invoice issue(
            UUID id,
            UUID claimId,
            UUID providerId,
            String invoiceNumber,
            Money totalAmount,
            Clock clock) {
        return new Invoice(id, claimId, providerId, invoiceNumber, totalAmount,
                InvoiceStatus.ISSUED, null, List.of(),
                Objects.requireNonNull(clock).instant(), null, null, 0);
    }

    public static Invoice rehydrate(
            UUID id,
            UUID claimId,
            UUID providerId,
            String invoiceNumber,
            Money totalAmount,
            InvoiceStatus status,
            Money payableAmount,
            List<Payment> payments,
            Instant issuedAt,
            Instant reconciledAt,
            Instant settledAt,
            long revision) {
        return new Invoice(id, claimId, providerId, invoiceNumber, totalAmount,
                status, payableAmount, payments, issuedAt, reconciledAt, settledAt, revision);
    }

    public void reconcile(Money approvedAmount, Clock clock) {
        requireStatus(InvoiceStatus.ISSUED, "Only an issued invoice can be reconciled");
        validatePayableAmount(approvedAmount);
        payableAmount = approvedAmount;
        status = approvedAmount.isEqualTo(totalAmount)
                ? InvoiceStatus.MATCHED : InvoiceStatus.DISPUTED;
        reconciledAt = Objects.requireNonNull(clock).instant();
    }

    public void resolveDispute(Money agreedPayableAmount, Clock clock) {
        requireStatus(InvoiceStatus.DISPUTED, "Only a disputed invoice can be resolved");
        validatePayableAmount(agreedPayableAmount);
        payableAmount = agreedPayableAmount;
        status = InvoiceStatus.MATCHED;
        reconciledAt = Objects.requireNonNull(clock).instant();
    }

    public void recordPayment(String reference, Money amount, Clock clock) {
        requireStatus(InvoiceStatus.MATCHED, "Payments require a matched invoice");
        String normalizedReference = requireText(reference, "paymentReference");
        if (payments.stream().anyMatch(payment -> payment.reference().equals(normalizedReference))) {
            throw new IllegalArgumentException("Payment reference has already been recorded");
        }
        Objects.requireNonNull(amount);
        Money nextPaid = paidAmount().add(amount);
        if (nextPaid.isGreaterThan(payableAmount)) {
            throw new IllegalArgumentException("Payment would exceed the payable amount");
        }
        Instant paidAt = Objects.requireNonNull(clock).instant();
        payments.add(new Payment(normalizedReference, amount, paidAt));
        if (nextPaid.isEqualTo(payableAmount)) {
            status = InvoiceStatus.SETTLED;
            settledAt = paidAt;
        }
    }

    public void voidDueToRejectedClaim() {
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.DISPUTED) {
            throw new InvalidInvoiceStateException(
                    "Only an unpaid issued or disputed invoice can be voided");
        }
        if (!payments.isEmpty()) {
            throw new InvalidInvoiceStateException("An invoice with payments cannot be voided");
        }
        status = InvoiceStatus.VOID;
        payableAmount = null;
    }

    public Money paidAmount() {
        Currency currency = totalAmount.currency();
        return payments.stream()
                .map(Payment::amount)
                .reduce(Money.zero(currency), Money::add);
    }

    private void validatePayableAmount(Money amount) {
        Objects.requireNonNull(amount);
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Payable amount must be positive");
        }
        totalAmount.requireSameCurrency(amount);
        if (amount.isGreaterThan(totalAmount)) {
            throw new IllegalArgumentException("Payable amount cannot exceed invoice total");
        }
    }

    private void validatePayments() {
        if (payments.stream().map(Payment::reference).distinct().count() != payments.size()) {
            throw new IllegalArgumentException("Payment references must be unique per invoice");
        }
        Money paid = paidAmount();
        if (!payments.isEmpty() && payableAmount == null) {
            throw new IllegalArgumentException("Paid invoice must define a payable amount");
        }
        if (payableAmount != null && paid.isGreaterThan(payableAmount)) {
            throw new IllegalArgumentException("Payments cannot exceed the payable amount");
        }
    }

    private void validateLifecycle() {
        if (reconciledAt != null && reconciledAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Reconciliation cannot precede invoice issuance");
        }
        if (settledAt != null && (reconciledAt == null || settledAt.isBefore(reconciledAt))) {
            throw new IllegalArgumentException("Settlement cannot precede reconciliation");
        }
        switch (status) {
            case ISSUED -> requireState(payableAmount == null && reconciledAt == null
                    && settledAt == null && payments.isEmpty(),
                    "Issued invoice cannot contain reconciliation or payment data");
            case MATCHED -> {
                requirePayableLifecycle("Matched invoice requires a payable amount and reconciliation time");
                requireState(settledAt == null
                                && paidAmount().amount().compareTo(payableAmount.amount()) < 0,
                        "Matched invoice must remain partially unpaid");
            }
            case DISPUTED -> {
                requirePayableLifecycle("Disputed invoice requires a payable amount and reconciliation time");
                requireState(settledAt == null && payments.isEmpty()
                                && payableAmount.amount().compareTo(totalAmount.amount()) < 0,
                        "Disputed invoice must be unpaid and below invoice total");
            }
            case SETTLED -> {
                requirePayableLifecycle("Settled invoice requires a payable amount and reconciliation time");
                requireState(settledAt != null && paidAmount().isEqualTo(payableAmount),
                        "Settled invoice payments must equal the payable amount");
            }
            case VOID -> requireState(payableAmount == null && settledAt == null && payments.isEmpty(),
                    "Void invoice cannot contain payable or payment data");
        }
    }

    private void requirePayableLifecycle(String message) {
        requireState(payableAmount != null && reconciledAt != null, message);
        validatePayableAmount(payableAmount);
    }

    private static void requireState(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private void requireStatus(InvoiceStatus expected, String message) {
        if (status != expected) {
            throw new InvalidInvoiceStateException(message);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public UUID claimId() { return claimId; }
    public UUID providerId() { return providerId; }
    public String invoiceNumber() { return invoiceNumber; }
    public Money totalAmount() { return totalAmount; }
    public InvoiceStatus status() { return status; }
    public Money payableAmount() { return payableAmount; }
    public List<Payment> payments() { return List.copyOf(payments); }
    public Instant issuedAt() { return issuedAt; }
    public Instant reconciledAt() { return reconciledAt; }
    public Instant settledAt() { return settledAt; }
    public long revision() { return revision; }
}
