package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.command.RecordPaymentCommand;
import com.aydindemir.health.claims.application.command.ResolveInvoiceDisputeCommand;
import com.aydindemir.health.claims.application.dto.InvoiceResult;

public interface ManageInvoiceUseCase {
    InvoiceResult resolveDispute(ResolveInvoiceDisputeCommand command);

    InvoiceResult recordPayment(RecordPaymentCommand command);
}
