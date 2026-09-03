package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.command.ApproveClaimCommand;
import com.aydindemir.health.claims.application.command.ClaimActionCommand;
import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.command.RecordPaymentCommand;
import com.aydindemir.health.claims.application.command.ResolveInvoiceDisputeCommand;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.GetClaimsBillingUseCase;
import com.aydindemir.health.claims.application.port.in.ManageInvoiceUseCase;
import com.aydindemir.health.claims.application.port.in.ReviewClaimUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;
import java.util.UUID;
import com.aydindemir.health.claims.application.query.GetClaimQuery;
import com.aydindemir.health.claims.application.query.GetInvoiceQuery;

@RestController
@RequestMapping("/api/v1")
class ClaimsBillingController {
    private final CreateClaimUseCase createClaim;
    private final ReviewClaimUseCase reviewClaim;
    private final ManageInvoiceUseCase manageInvoice;
    private final GetClaimsBillingUseCase getClaimsBilling;
    private final AuthenticatedActorMapper actorMapper;

    ClaimsBillingController(CreateClaimUseCase createClaim, ReviewClaimUseCase reviewClaim,
                            ManageInvoiceUseCase manageInvoice, GetClaimsBillingUseCase getClaimsBilling,
                            AuthenticatedActorMapper actorMapper) {
        this.createClaim = createClaim;
        this.reviewClaim = reviewClaim;
        this.manageInvoice = manageInvoice;
        this.getClaimsBilling = getClaimsBilling;
        this.actorMapper = actorMapper;
    }

    @GetMapping("/claims/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'CLAIM_APPROVER', 'SYSTEM_ADMIN')")
    ClaimsBillingResponses.Claim getClaim(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.Claim.from(getClaimsBilling.getClaim(
                new GetClaimQuery(actorMapper.from(authentication), id)));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'CLAIM_APPROVER', 'SYSTEM_ADMIN')")
    ClaimsBillingResponses.Invoice getInvoice(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.Invoice.from(getClaimsBilling.getInvoice(
                new GetInvoiceQuery(actorMapper.from(authentication), id)));
    }

    @PostMapping("/claims")
    @PreAuthorize("hasRole('HOSPITAL_USER')")
    ResponseEntity<ClaimsBillingResponses.ClaimInvoice> create(
            @Valid @RequestBody CreateClaimRequest request, JwtAuthenticationToken authentication) {
        var result = createClaim.create(new CreateClaimCommand(
                actorMapper.from(authentication), request.preAuthorizationId(), request.invoiceNumber(),
                request.invoicedAmount(), Currency.getInstance(request.currency())));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.claim().id()).toUri();
        return ResponseEntity.created(location).body(ClaimsBillingResponses.ClaimInvoice.from(result));
    }

    @PostMapping("/claims/{id}/review")
    @PreAuthorize("hasRole('CLAIM_APPROVER')")
    ClaimsBillingResponses.Claim startReview(@PathVariable UUID id,
                                             JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.Claim.from(reviewClaim.startReview(
                new ClaimActionCommand(actorMapper.from(authentication), id, null)));
    }

    @PostMapping("/claims/{id}/approval")
    @PreAuthorize("hasRole('CLAIM_APPROVER')")
    ClaimsBillingResponses.ClaimInvoice approve(
            @PathVariable UUID id, @Valid @RequestBody FinancialRequests.Amount request,
            JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.ClaimInvoice.from(reviewClaim.approve(new ApproveClaimCommand(
                actorMapper.from(authentication), id, request.amount(), Currency.getInstance(request.currency()))));
    }

    @PostMapping("/claims/{id}/rejection")
    @PreAuthorize("hasRole('CLAIM_APPROVER')")
    ClaimsBillingResponses.ClaimInvoice reject(
            @PathVariable UUID id, @Valid @RequestBody FinancialRequests.Rejection request,
            JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.ClaimInvoice.from(reviewClaim.reject(
                new ClaimActionCommand(actorMapper.from(authentication), id, request.reason())));
    }

    @PostMapping("/invoices/{id}/dispute-resolution")
    @PreAuthorize("hasAnyRole('INSURANCE_SPECIALIST', 'SYSTEM_ADMIN')")
    ClaimsBillingResponses.Invoice resolveDispute(
            @PathVariable UUID id, @Valid @RequestBody FinancialRequests.Amount request,
            JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.Invoice.from(manageInvoice.resolveDispute(
                new ResolveInvoiceDisputeCommand(actorMapper.from(authentication), id,
                        request.amount(), Currency.getInstance(request.currency()))));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAnyRole('INSURANCE_SPECIALIST', 'SYSTEM_ADMIN')")
    ClaimsBillingResponses.Invoice recordPayment(
            @PathVariable UUID id, @Valid @RequestBody FinancialRequests.Payment request,
            JwtAuthenticationToken authentication) {
        return ClaimsBillingResponses.Invoice.from(manageInvoice.recordPayment(
                new RecordPaymentCommand(actorMapper.from(authentication), id,
                        request.paymentReference(), request.amount(), Currency.getInstance(request.currency()))));
    }
}
