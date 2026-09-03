package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.model.Invoice;
import com.aydindemir.health.claims.domain.valueobject.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaClaimRepositoryAdapter.class, JpaInvoiceRepositoryAdapter.class})
class JpaClaimsBillingRepositoryIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    private static final Currency TRY = Currency.getInstance("TRY");

    @Autowired ClaimRepository claims;
    @Autowired InvoiceRepository invoices;
    @Autowired JdbcTemplate jdbc;

    @Test
    void appliesMigrationAndRoundTripsBothAggregates() {
        UUID claimId = UUID.randomUUID();
        Claim claim = Claim.submit(claimId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "POL-100", "IMG-MRI", money("1000.00"), CLOCK);
        Invoice invoice = Invoice.issue(UUID.randomUUID(), claimId, claim.providerId(),
                "INV-100", money("1000.00"), CLOCK);

        claims.save(claim);
        invoices.save(invoice);
        claim.startReview(CLOCK);
        claim.approve(money("900.00"), CLOCK);
        invoice.reconcile(money("900.00"), CLOCK);
        invoice.resolveDispute(money("850.00"), CLOCK);
        invoice.recordPayment("PAY-001", money("850.00"), CLOCK);
        claims.save(claim);
        invoices.save(invoice);

        assertThat(claims.findById(claimId)).hasValueSatisfying(value ->
                assertThat(value.approvedAmount().amount()).isEqualByComparingTo("900.00"));
        assertThat(invoices.findByClaimId(claimId)).hasValueSatisfying(value -> {
            assertThat(value.status().name()).isEqualTo("SETTLED");
            assertThat(value.payments()).singleElement().satisfies(payment ->
                    assertThat(payment.reference()).isEqualTo("PAY-001"));
        });
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void createsOwnershipAndUniquenessIndexes() {
        var indexes = jdbc.queryForList(
                "select indexname from pg_indexes where tablename in ('claims','invoices','invoice_payments')",
                String.class);
        assertThat(indexes).contains("uk_claims_pre_authorization", "uk_invoices_claim",
                "uk_invoices_number_lower", "idx_claims_provider_status", "idx_invoices_provider_status");
    }

    private Money money(String amount) { return new Money(new BigDecimal(amount), TRY); }
}
