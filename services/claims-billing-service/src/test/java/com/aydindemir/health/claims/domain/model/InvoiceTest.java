package com.aydindemir.health.claims.domain.model;

import com.aydindemir.health.claims.domain.exception.InvalidInvoiceStateException;
import com.aydindemir.health.claims.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    void matchesInvoiceWhenApprovedAndInvoicedAmountsAreEqual() {
        Invoice invoice = invoice();

        invoice.reconcile(money("1000.00"), CLOCK);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.MATCHED);
        assertThat(invoice.payableAmount().amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void opensDisputeWhenApprovedAmountDiffersFromInvoice() {
        Invoice invoice = invoice();

        invoice.reconcile(money("800.00"), CLOCK);

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DISPUTED);
        invoice.resolveDispute(money("850.00"), CLOCK);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.MATCHED);
    }

    @Test
    void settlesOnlyAfterPaymentsReachPayableAmount() {
        Invoice invoice = invoice();
        invoice.reconcile(money("1000.00"), CLOCK);

        invoice.recordPayment("PAY-001", money("400.00"), CLOCK);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.MATCHED);
        assertThat(invoice.paidAmount().amount()).isEqualByComparingTo("400.00");

        invoice.recordPayment("PAY-002", money("600.00"), CLOCK);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.SETTLED);
        assertThat(invoice.settledAt()).isEqualTo(NOW);
    }

    @Test
    void preventsDuplicatePaymentReferencesAndOverpayment() {
        Invoice invoice = invoice();
        invoice.reconcile(money("1000.00"), CLOCK);
        invoice.recordPayment("PAY-001", money("400.00"), CLOCK);

        assertThatThrownBy(() -> invoice.recordPayment(" PAY-001 ", money("100.00"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been recorded");
        assertThatThrownBy(() -> invoice.recordPayment("PAY-002", money("700.00"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed the payable");
    }

    @Test
    void voidsUnpaidInvoiceAfterClaimRejection() {
        Invoice invoice = invoice();

        invoice.voidDueToRejectedClaim();

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOID);
    }

    @Test
    void preventsPaymentBeforeReconciliation() {
        assertThatThrownBy(() -> invoice().recordPayment("PAY-001", money("100.00"), CLOCK))
                .isInstanceOf(InvalidInvoiceStateException.class)
                .hasMessageContaining("matched invoice");
    }

    private Invoice invoice() {
        return Invoice.issue(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "INV-100", money("1000.00"), CLOCK);
    }

    private Money money(String value) {
        return new Money(new BigDecimal(value), TRY);
    }
}
