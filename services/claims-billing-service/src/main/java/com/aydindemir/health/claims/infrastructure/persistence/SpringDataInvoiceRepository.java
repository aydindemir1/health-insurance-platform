package com.aydindemir.health.claims.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataInvoiceRepository extends JpaRepository<InvoiceJpaEntity, UUID> {
    Optional<InvoiceJpaEntity> findByClaimId(UUID claimId);
    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);
}
