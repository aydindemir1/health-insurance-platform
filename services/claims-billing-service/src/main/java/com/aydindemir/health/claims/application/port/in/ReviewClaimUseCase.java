package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.command.ApproveClaimCommand;
import com.aydindemir.health.claims.application.command.ClaimActionCommand;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimResult;

public interface ReviewClaimUseCase {
    ClaimResult startReview(ClaimActionCommand command);

    ClaimInvoiceResult approve(ApproveClaimCommand command);

    ClaimInvoiceResult reject(ClaimActionCommand command);
}
