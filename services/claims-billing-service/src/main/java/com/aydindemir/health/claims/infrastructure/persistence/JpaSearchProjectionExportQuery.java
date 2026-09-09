package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.event.ClaimSearchProjection;
import com.aydindemir.health.claims.application.port.out.SearchProjectionExportQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaSearchProjectionExportQuery implements SearchProjectionExportQuery {
    private final SpringDataClaimRepository repository;

    JpaSearchProjectionExportQuery(SpringDataClaimRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<ClaimSearchProjection> findPage(int page, int size) {
        var result = repository.exportSearchProjections(PageRequest.of(page, size));
        return new PageResult<>(
                result.getContent().stream().map(this::toProjection).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private ClaimSearchProjection toProjection(ClaimSearchProjectionRow row) {
        return new ClaimSearchProjection(
                row.getClaimId(), row.getInvoiceId(), row.getPreAuthorizationId(),
                row.getMemberId(), row.getProviderId(), row.getPolicyNumber(), row.getServiceCode(),
                row.getClaimedAmount(), row.getApprovedAmount(), row.getPayableAmount(),
                row.getPaidAmount(), row.getCurrency(), row.getClaimStatus(), row.getInvoiceStatus(),
                row.getInvoiceNumber(), row.getSourceRevision(), row.getOccurredAt());
    }
}
