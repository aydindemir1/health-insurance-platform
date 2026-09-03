package com.aydindemir.health.claims.application.usecase;

import com.aydindemir.health.claims.application.command.ApproveClaimCommand;
import com.aydindemir.health.claims.application.command.ClaimActionCommand;
import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.command.RecordPaymentCommand;
import com.aydindemir.health.claims.application.command.ResolveInvoiceDisputeCommand;
import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.exception.ApprovedPreAuthorizationRequiredException;
import com.aydindemir.health.claims.application.exception.DuplicateClaimException;
import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import com.aydindemir.health.claims.application.query.GetClaimQuery;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimsBillingApplicationServiceTest {
    private static final UUID PRE_AUTHORIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CLAIM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID INVOICE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PROVIDER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROVIDER_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryClaimRepository claims;
    private InMemoryInvoiceRepository invoices;
    private ClaimsBillingApplicationService service;

    @BeforeEach
    void setUp() {
        claims = new InMemoryClaimRepository();
        invoices = new InMemoryInvoiceRepository();
        service = serviceWith(snapshot("APPROVED", PROVIDER_ID));
    }

    @Test
    void createsClaimAndInvoiceFromApprovedOwnedPreAuthorization() {
        var result = service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));

        assertThat(result.claim().id()).isEqualTo(CLAIM_ID);
        assertThat(result.claim().status()).isEqualTo("SUBMITTED");
        assertThat(result.claim().memberId()).isEqualTo(MEMBER_ID);
        assertThat(result.invoice().id()).isEqualTo(INVOICE_ID);
        assertThat(result.invoice().claimId()).isEqualTo(CLAIM_ID);
        assertThat(result.invoice().status()).isEqualTo("ISSUED");
    }

    @Test
    void rejectsNonApprovedPreAuthorization() {
        service = serviceWith(snapshot("PENDING", PROVIDER_ID));

        assertThatThrownBy(() -> service.create(
                createCommand(hospital(PROVIDER_ID), "1000.00")))
                .isInstanceOf(ApprovedPreAuthorizationRequiredException.class);
        assertThat(claims.entries).isEmpty();
        assertThat(invoices.entries).isEmpty();
    }

    @Test
    void enforcesProviderOwnershipFromAuthenticatedActor() {
        assertThatThrownBy(() -> service.create(
                createCommand(hospital(OTHER_PROVIDER_ID), "1000.00")))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("own provider");
    }

    @Test
    void preventsClaimAboveAuthorizedAmount() {
        assertThatThrownBy(() -> service.create(
                createCommand(hospital(PROVIDER_ID), "1250.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorized amount");
    }

    @Test
    void preventsSecondClaimForSamePreAuthorization() {
        service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));

        assertThatThrownBy(() -> service.create(
                createCommand(hospital(PROVIDER_ID), "1000.00")))
                .isInstanceOf(DuplicateClaimException.class);
    }

    @Test
    void scopesHospitalReadsToAuthenticatedProvider() {
        service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));

        assertThat(service.getClaim(new GetClaimQuery(hospital(PROVIDER_ID), CLAIM_ID)).id())
                .isEqualTo(CLAIM_ID);
        assertThatThrownBy(() -> service.getClaim(
                new GetClaimQuery(hospital(OTHER_PROVIDER_ID), CLAIM_ID)))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("own provider");
    }

    @Test
    void approvalReconcilesInvoiceAndOpensDisputeForPartialApproval() {
        service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));
        service.startReview(new ClaimActionCommand(approver(), CLAIM_ID, null));

        var result = service.approve(new ApproveClaimCommand(
                approver(), CLAIM_ID, new BigDecimal("800.00"), TRY));

        assertThat(result.claim().status()).isEqualTo("APPROVED");
        assertThat(result.invoice().status()).isEqualTo("DISPUTED");
        assertThat(result.invoice().payableAmount()).isEqualByComparingTo("800.00");
    }

    @Test
    void rejectionVoidsInvoice() {
        service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));
        service.startReview(new ClaimActionCommand(approver(), CLAIM_ID, null));

        var result = service.reject(new ClaimActionCommand(
                approver(), CLAIM_ID, "Clinical document is missing"));

        assertThat(result.claim().status()).isEqualTo("REJECTED");
        assertThat(result.invoice().status()).isEqualTo("VOID");
    }

    @Test
    void resolvesDisputeAndSettlesInvoiceWithPayment() {
        service.create(createCommand(hospital(PROVIDER_ID), "1000.00"));
        service.startReview(new ClaimActionCommand(approver(), CLAIM_ID, null));
        service.approve(new ApproveClaimCommand(
                approver(), CLAIM_ID, new BigDecimal("800.00"), TRY));
        service.resolveDispute(new ResolveInvoiceDisputeCommand(
                specialist(), INVOICE_ID, new BigDecimal("800.00"), TRY));

        var result = service.recordPayment(new RecordPaymentCommand(
                specialist(), INVOICE_ID, "PAY-100", new BigDecimal("800.00"), TRY));

        assertThat(result.status()).isEqualTo("SETTLED");
        assertThat(result.paidAmount()).isEqualByComparingTo("800.00");
    }

    private ClaimsBillingApplicationService serviceWith(
            ApprovedPreAuthorizationPort.PreAuthorizationSnapshot snapshot) {
        Queue<UUID> ids = new ArrayDeque<>();
        ids.add(CLAIM_ID);
        ids.add(INVOICE_ID);
        return new ClaimsBillingApplicationService(
                claims, invoices,
                id -> PRE_AUTHORIZATION_ID.equals(id) ? Optional.of(snapshot) : Optional.empty(),
                ids::remove, CLOCK);
    }

    private ApprovedPreAuthorizationPort.PreAuthorizationSnapshot snapshot(
            String status, UUID providerId) {
        return new ApprovedPreAuthorizationPort.PreAuthorizationSnapshot(
                PRE_AUTHORIZATION_ID, MEMBER_ID, providerId, "POL-100", "IMG-MRI",
                new BigDecimal("1250.00"), TRY, status);
    }

    private CreateClaimCommand createCommand(ActorContext actor, String amount) {
        return new CreateClaimCommand(
                actor, PRE_AUTHORIZATION_ID, "INV-100", new BigDecimal(amount), TRY);
    }

    private ActorContext hospital(UUID providerId) {
        return new ActorContext(
                "hospital", providerId, Set.of(ApplicationRole.HOSPITAL_USER));
    }

    private ActorContext approver() {
        return new ActorContext(
                "approver", null, Set.of(ApplicationRole.CLAIM_APPROVER));
    }

    private ActorContext specialist() {
        return new ActorContext(
                "specialist", null, Set.of(ApplicationRole.INSURANCE_SPECIALIST));
    }

    private static final class InMemoryClaimRepository implements ClaimRepository {
        private final Map<UUID, Claim> entries = new HashMap<>();

        @Override
        public Claim save(Claim claim) {
            entries.put(claim.id(), claim);
            return claim;
        }

        @Override
        public Optional<Claim> findById(UUID id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public boolean existsByPreAuthorizationId(UUID preAuthorizationId) {
            return entries.values().stream()
                    .anyMatch(claim -> claim.preAuthorizationId().equals(preAuthorizationId));
        }
    }

    private static final class InMemoryInvoiceRepository implements InvoiceRepository {
        private final Map<UUID, Invoice> entries = new HashMap<>();

        @Override
        public Invoice save(Invoice invoice) {
            entries.put(invoice.id(), invoice);
            return invoice;
        }

        @Override
        public Optional<Invoice> findById(UUID id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public Optional<Invoice> findByClaimId(UUID claimId) {
            return entries.values().stream()
                    .filter(invoice -> invoice.claimId().equals(claimId))
                    .findFirst();
        }

        @Override
        public boolean existsByInvoiceNumber(String invoiceNumber) {
            return entries.values().stream()
                    .anyMatch(invoice -> invoice.invoiceNumber().equalsIgnoreCase(invoiceNumber));
        }
    }
}
