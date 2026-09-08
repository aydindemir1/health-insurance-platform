package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.query.GetClaimByPreAuthorizationQuery;
import com.aydindemir.health.claims.application.query.GetClaimQuery;
import com.aydindemir.health.claims.application.query.GetInvoiceQuery;

public interface GetClaimsBillingUseCase {
    ClaimResult getClaim(GetClaimQuery query);
    InvoiceResult getInvoice(GetInvoiceQuery query);
    ClaimInvoiceResult getByPreAuthorization(GetClaimByPreAuthorizationQuery query);
}
