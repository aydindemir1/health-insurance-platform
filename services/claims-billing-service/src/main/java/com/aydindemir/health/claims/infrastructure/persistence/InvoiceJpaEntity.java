package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.domain.model.InvoiceStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
class InvoiceJpaEntity {
    @Id UUID id;
    @Column(name = "claim_id", nullable = false) UUID claimId;
    @Column(name = "provider_id", nullable = false) UUID providerId;
    @Column(name = "invoice_number", nullable = false, length = 80) String invoiceNumber;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2) BigDecimal totalAmount;
    @Column(name = "payable_amount", precision = 19, scale = 2) BigDecimal payableAmount;
    @Column(name = "currency", nullable = false, length = 3) String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) InvoiceStatus status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_payments", joinColumns = @JoinColumn(name = "invoice_id"))
    List<PaymentJpaEmbeddable> payments = new ArrayList<>();
    @Column(name = "issued_at", nullable = false) Instant issuedAt;
    @Column(name = "reconciled_at") Instant reconciledAt;
    @Column(name = "settled_at") Instant settledAt;
    @Version @Column(name = "version", nullable = false) long version;
}
