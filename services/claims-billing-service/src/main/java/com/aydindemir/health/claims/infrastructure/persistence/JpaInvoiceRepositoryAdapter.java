package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.application.exception.ConcurrentClaimsBillingUpdateException;
import com.aydindemir.health.claims.application.exception.InvoiceNumberConflictException;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.domain.model.Invoice;
import com.aydindemir.health.claims.domain.model.Payment;
import com.aydindemir.health.claims.domain.valueobject.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInvoiceRepositoryAdapter implements InvoiceRepository {
    private final SpringDataInvoiceRepository repository;

    JpaInvoiceRepositoryAdapter(SpringDataInvoiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Invoice save(Invoice invoice) {
        var entity = repository.findById(invoice.id()).orElseGet(InvoiceJpaEntity::new);
        mapToEntity(invoice, entity);
        try {
            return mapToDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new InvoiceNumberConflictException(invoice.invoiceNumber(), exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentClaimsBillingUpdateException("Invoice", exception);
        }
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    @Override
    public Optional<Invoice> findByClaimId(UUID claimId) {
        return repository.findByClaimId(claimId).map(this::mapToDomain);
    }

    @Override
    public boolean existsByInvoiceNumber(String invoiceNumber) {
        return repository.existsByInvoiceNumberIgnoreCase(invoiceNumber);
    }

    private void mapToEntity(Invoice source, InvoiceJpaEntity target) {
        target.id = source.id();
        target.claimId = source.claimId();
        target.providerId = source.providerId();
        target.invoiceNumber = source.invoiceNumber();
        target.totalAmount = source.totalAmount().amount();
        target.payableAmount = source.payableAmount() == null ? null : source.payableAmount().amount();
        target.currency = source.totalAmount().currency().getCurrencyCode();
        target.status = source.status();
        target.payments.clear();
        source.payments().stream().map(this::mapPayment).forEach(target.payments::add);
        target.issuedAt = source.issuedAt();
        target.reconciledAt = source.reconciledAt();
        target.settledAt = source.settledAt();
    }

    private PaymentJpaEmbeddable mapPayment(Payment source) {
        var target = new PaymentJpaEmbeddable();
        target.reference = source.reference();
        target.amount = source.amount().amount();
        target.paidAt = source.paidAt();
        return target;
    }

    private Invoice mapToDomain(InvoiceJpaEntity source) {
        Currency currency = Currency.getInstance(source.currency);
        var payments = source.payments.stream()
                .map(payment -> new Payment(
                        payment.reference, new Money(payment.amount, currency), payment.paidAt))
                .toList();
        return Invoice.rehydrate(
                source.id, source.claimId, source.providerId, source.invoiceNumber,
                new Money(source.totalAmount, currency), source.status,
                source.payableAmount == null ? null : new Money(source.payableAmount, currency),
                payments, source.issuedAt, source.reconciledAt, source.settledAt);
    }
}
