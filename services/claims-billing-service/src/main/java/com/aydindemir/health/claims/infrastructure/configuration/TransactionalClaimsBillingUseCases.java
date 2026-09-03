package com.aydindemir.health.claims.infrastructure.configuration;

import com.aydindemir.health.claims.application.command.ApproveClaimCommand;
import com.aydindemir.health.claims.application.command.ClaimActionCommand;
import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.command.RecordPaymentCommand;
import com.aydindemir.health.claims.application.command.ResolveInvoiceDisputeCommand;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.GetClaimsBillingUseCase;
import com.aydindemir.health.claims.application.port.in.ManageInvoiceUseCase;
import com.aydindemir.health.claims.application.port.in.ReviewClaimUseCase;
import com.aydindemir.health.claims.application.usecase.ClaimsBillingApplicationService;
import com.aydindemir.health.claims.application.query.GetClaimQuery;
import com.aydindemir.health.claims.application.query.GetInvoiceQuery;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalClaimsBillingUseCases implements
        CreateClaimUseCase, ReviewClaimUseCase, ManageInvoiceUseCase, GetClaimsBillingUseCase {
    private final ClaimsBillingApplicationService delegate;

    TransactionalClaimsBillingUseCases(ClaimsBillingApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override @Transactional
    public ClaimInvoiceResult create(CreateClaimCommand command) { return delegate.create(command); }

    @Override @Transactional
    public ClaimResult startReview(ClaimActionCommand command) { return delegate.startReview(command); }

    @Override @Transactional
    public ClaimInvoiceResult approve(ApproveClaimCommand command) { return delegate.approve(command); }

    @Override @Transactional
    public ClaimInvoiceResult reject(ClaimActionCommand command) { return delegate.reject(command); }

    @Override @Transactional
    public InvoiceResult resolveDispute(ResolveInvoiceDisputeCommand command) {
        return delegate.resolveDispute(command);
    }

    @Override @Transactional
    public InvoiceResult recordPayment(RecordPaymentCommand command) {
        return delegate.recordPayment(command);
    }

    @Override @Transactional(readOnly = true)
    public ClaimResult getClaim(GetClaimQuery query) { return delegate.getClaim(query); }

    @Override @Transactional(readOnly = true)
    public InvoiceResult getInvoice(GetInvoiceQuery query) { return delegate.getInvoice(query); }
}
