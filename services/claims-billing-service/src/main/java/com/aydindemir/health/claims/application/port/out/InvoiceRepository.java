package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.domain.model.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    Optional<Invoice> findByClaimId(UUID claimId);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
