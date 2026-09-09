package com.aydindemir.health.claims.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

interface SpringDataClaimRepository extends JpaRepository<ClaimJpaEntity, UUID> {
    boolean existsByPreAuthorizationId(UUID preAuthorizationId);
    Optional<ClaimJpaEntity> findByPreAuthorizationId(UUID preAuthorizationId);

    @Query(value = """
            select c.id as claimId, i.id as invoiceId,
                   c.pre_authorization_id as preAuthorizationId,
                   c.member_id as memberId, c.provider_id as providerId,
                   c.policy_number as policyNumber, c.service_code as serviceCode,
                   c.claimed_amount as claimedAmount, c.approved_amount as approvedAmount,
                   i.payable_amount as payableAmount, coalesce(sum(p.amount), 0) as paidAmount,
                   c.currency as currency, c.status as claimStatus,
                   i.status as invoiceStatus, i.invoice_number as invoiceNumber,
                   (c.version + i.version + 1) as sourceRevision,
                   greatest(c.submitted_at, coalesce(c.review_started_at, c.submitted_at),
                            coalesce(c.decided_at, c.submitted_at), i.issued_at,
                            coalesce(i.reconciled_at, i.issued_at),
                            coalesce(i.settled_at, i.issued_at)) as occurredAt
              from claims c
              join invoices i on i.claim_id = c.id
              left join invoice_payments p on p.invoice_id = i.id
             group by c.id, i.id
             order by c.id
            """,
            countQuery = "select count(*) from claims",
            nativeQuery = true)
    Page<ClaimSearchProjectionRow> exportSearchProjections(Pageable pageable);
}
