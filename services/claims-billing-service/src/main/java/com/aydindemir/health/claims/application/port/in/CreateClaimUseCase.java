package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;

public interface CreateClaimUseCase {
    ClaimInvoiceResult create(CreateClaimCommand command);
}
