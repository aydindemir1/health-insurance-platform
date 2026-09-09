package com.aydindemir.health.claims.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

interface ClaimSearchProjectionRow {
    UUID getClaimId();
    UUID getInvoiceId();
    UUID getPreAuthorizationId();
    UUID getMemberId();
    UUID getProviderId();
    String getPolicyNumber();
    String getServiceCode();
    BigDecimal getClaimedAmount();
    BigDecimal getApprovedAmount();
    BigDecimal getPayableAmount();
    BigDecimal getPaidAmount();
    String getCurrency();
    String getClaimStatus();
    String getInvoiceStatus();
    String getInvoiceNumber();
    Long getSourceRevision();
    Instant getOccurredAt();
}
