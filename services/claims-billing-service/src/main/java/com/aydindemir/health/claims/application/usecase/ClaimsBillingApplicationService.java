package com.aydindemir.health.claims.application.usecase;

import com.aydindemir.health.claims.application.command.ApproveClaimCommand;
import com.aydindemir.health.claims.application.command.ClaimActionCommand;
import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.command.RecordPaymentCommand;
import com.aydindemir.health.claims.application.command.ResolveInvoiceDisputeCommand;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;
import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.exception.ApprovedPreAuthorizationRequiredException;
import com.aydindemir.health.claims.application.exception.ClaimNotFoundException;
import com.aydindemir.health.claims.application.exception.ClaimsBillingStateConflictException;
import com.aydindemir.health.claims.application.exception.DuplicateClaimException;
import com.aydindemir.health.claims.application.exception.InvoiceNotFoundException;
import com.aydindemir.health.claims.application.exception.InvoiceNumberConflictException;
import com.aydindemir.health.claims.application.mapper.ClaimsBillingResultMapper;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.GetClaimsBillingUseCase;
import com.aydindemir.health.claims.application.port.in.ManageInvoiceUseCase;
import com.aydindemir.health.claims.application.port.in.ReviewClaimUseCase;
import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.application.port.out.IdentifierGenerator;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.query.GetClaimQuery;
import com.aydindemir.health.claims.application.query.GetInvoiceQuery;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.model.Invoice;
import com.aydindemir.health.claims.domain.valueobject.Money;
import com.aydindemir.health.claims.domain.exception.InvalidClaimStateException;
import com.aydindemir.health.claims.domain.exception.InvalidInvoiceStateException;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ClaimsBillingApplicationService implements
        CreateClaimUseCase, ReviewClaimUseCase, ManageInvoiceUseCase, GetClaimsBillingUseCase {
    private final ClaimRepository claims;
    private final InvoiceRepository invoices;
    private final ApprovedPreAuthorizationPort preAuthorizations;
    private final IdentifierGenerator identifiers;
    private final Clock clock;

    public ClaimsBillingApplicationService(
            ClaimRepository claims,
            InvoiceRepository invoices,
            ApprovedPreAuthorizationPort preAuthorizations,
            IdentifierGenerator identifiers,
            Clock clock) {
        this.claims = Objects.requireNonNull(claims);
        this.invoices = Objects.requireNonNull(invoices);
        this.preAuthorizations = Objects.requireNonNull(preAuthorizations);
        this.identifiers = Objects.requireNonNull(identifiers);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ClaimInvoiceResult create(CreateClaimCommand command) {
        Objects.requireNonNull(command);
        requireRole(command.actor(), ApplicationRole.HOSPITAL_USER);
        UUID providerId = requireProvider(command.actor());
        if (claims.existsByPreAuthorizationId(command.preAuthorizationId())) {
            throw new DuplicateClaimException(command.preAuthorizationId());
        }
        if (invoices.existsByInvoiceNumber(command.invoiceNumber())) {
            throw new InvoiceNumberConflictException(command.invoiceNumber());
        }
        var authorization = preAuthorizations.findById(command.preAuthorizationId())
                .filter(snapshot -> "APPROVED".equals(snapshot.status()))
                .orElseThrow(() -> new ApprovedPreAuthorizationRequiredException(
                        command.preAuthorizationId()));
        if (!providerId.equals(authorization.providerId())) {
            throw new ApplicationAccessDeniedException(
                    "Hospital users can only claim their own provider's pre-authorizations");
        }
        Money invoicedAmount = Money.positive(command.invoicedAmount(), command.currency());
        Money authorizedAmount = Money.positive(
                authorization.authorizedAmount(), authorization.currency());
        if (invoicedAmount.isGreaterThan(authorizedAmount)) {
            throw new IllegalArgumentException(
                    "Invoiced amount cannot exceed the authorized amount");
        }
        UUID claimId = identifiers.generate();
        Claim claim = Claim.submit(
                claimId, authorization.id(), authorization.memberId(), providerId,
                authorization.policyNumber(), authorization.serviceCode(), invoicedAmount, clock);
        Invoice invoice = Invoice.issue(
                identifiers.generate(), claimId, providerId,
                command.invoiceNumber(), invoicedAmount, clock);
        return result(claims.save(claim), invoices.save(invoice));
    }

    @Override
    public ClaimResult startReview(ClaimActionCommand command) {
        return withStateConflict(() -> {
            Objects.requireNonNull(command);
            requireRole(command.actor(), ApplicationRole.CLAIM_APPROVER);
            Claim claim = findClaim(command.claimId());
            claim.startReview(clock);
            return ClaimsBillingResultMapper.toResult(claims.save(claim));
        });
    }

    @Override
    public ClaimInvoiceResult approve(ApproveClaimCommand command) {
        return withStateConflict(() -> {
            Objects.requireNonNull(command);
            requireRole(command.actor(), ApplicationRole.CLAIM_APPROVER);
            Claim claim = findClaim(command.claimId());
            Invoice invoice = findInvoiceByClaimId(claim.id());
            claim.approve(Money.positive(command.approvedAmount(), command.currency()), clock);
            invoice.reconcile(claim.approvedAmount(), clock);
            return result(claims.save(claim), invoices.save(invoice));
        });
    }

    @Override
    public ClaimInvoiceResult reject(ClaimActionCommand command) {
        return withStateConflict(() -> {
            Objects.requireNonNull(command);
            requireRole(command.actor(), ApplicationRole.CLAIM_APPROVER);
            Claim claim = findClaim(command.claimId());
            Invoice invoice = findInvoiceByClaimId(claim.id());
            claim.reject(command.reason(), clock);
            invoice.voidDueToRejectedClaim();
            return result(claims.save(claim), invoices.save(invoice));
        });
    }

    @Override
    public InvoiceResult resolveDispute(ResolveInvoiceDisputeCommand command) {
        return withStateConflict(() -> {
            Objects.requireNonNull(command);
            requireFinancialRole(command.actor());
            Invoice invoice = findInvoice(command.invoiceId());
            invoice.resolveDispute(
                    Money.positive(command.agreedPayableAmount(), command.currency()), clock);
            return ClaimsBillingResultMapper.toResult(invoices.save(invoice));
        });
    }

    @Override
    public InvoiceResult recordPayment(RecordPaymentCommand command) {
        return withStateConflict(() -> {
            Objects.requireNonNull(command);
            requireFinancialRole(command.actor());
            Invoice invoice = findInvoice(command.invoiceId());
            invoice.recordPayment(
                    command.paymentReference(), Money.positive(command.amount(), command.currency()), clock);
            return ClaimsBillingResultMapper.toResult(invoices.save(invoice));
        });
    }

    @Override
    public ClaimResult getClaim(GetClaimQuery query) {
        Claim claim = findClaim(Objects.requireNonNull(query).claimId());
        requireReadAccess(query.actor(), claim.providerId());
        return ClaimsBillingResultMapper.toResult(claim);
    }

    @Override
    public InvoiceResult getInvoice(GetInvoiceQuery query) {
        Invoice invoice = findInvoice(Objects.requireNonNull(query).invoiceId());
        requireReadAccess(query.actor(), invoice.providerId());
        return ClaimsBillingResultMapper.toResult(invoice);
    }

    private <T> T withStateConflict(Supplier<T> action) {
        try {
            return action.get();
        } catch (InvalidClaimStateException | InvalidInvoiceStateException exception) {
            throw new ClaimsBillingStateConflictException(exception.getMessage(), exception);
        }
    }

    private ClaimInvoiceResult result(Claim claim, Invoice invoice) {
        return new ClaimInvoiceResult(
                ClaimsBillingResultMapper.toResult(claim),
                ClaimsBillingResultMapper.toResult(invoice));
    }

    private Claim findClaim(UUID id) {
        return claims.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    private Invoice findInvoice(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    private Invoice findInvoiceByClaimId(UUID claimId) {
        return invoices.findByClaimId(claimId)
                .orElseThrow(() -> new IllegalStateException(
                        "Claim has no invoice: " + claimId));
    }

    private void requireFinancialRole(ActorContext actor) {
        if (!actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                && !actor.hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException(
                    "Financial operations require INSURANCE_SPECIALIST or SYSTEM_ADMIN");
        }
    }

    private void requireReadAccess(ActorContext actor, UUID providerId) {
        Objects.requireNonNull(actor);
        if (actor.hasRole(ApplicationRole.SYSTEM_ADMIN)
                || actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                || actor.hasRole(ApplicationRole.CLAIM_APPROVER)) return;
        if (actor.hasRole(ApplicationRole.HOSPITAL_USER)
                && providerId.equals(actor.providerId())) return;
        throw new ApplicationAccessDeniedException(
                "Hospital users can only view their own provider's claims and invoices");
    }

    private void requireRole(ActorContext actor, ApplicationRole role) {
        Objects.requireNonNull(actor);
        if (!actor.hasRole(role)) {
            throw new ApplicationAccessDeniedException("Required role: " + role);
        }
    }

    private UUID requireProvider(ActorContext actor) {
        if (actor.providerId() == null) {
            throw new ApplicationAccessDeniedException(
                    "A provider identity is required for hospital operations");
        }
        return actor.providerId();
    }
}
