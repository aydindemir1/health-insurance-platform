package com.aydindemir.health.claims.infrastructure.persistence;

import com.aydindemir.health.claims.application.exception.ConcurrentClaimsBillingUpdateException;
import com.aydindemir.health.claims.application.exception.DuplicateClaimException;
import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.valueobject.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaClaimRepositoryAdapter implements ClaimRepository {
    private final SpringDataClaimRepository repository;

    JpaClaimRepositoryAdapter(SpringDataClaimRepository repository) {
        this.repository = repository;
    }

    @Override
    public Claim save(Claim claim) {
        var entity = repository.findById(claim.id()).orElseGet(ClaimJpaEntity::new);
        mapToEntity(claim, entity);
        try {
            return mapToDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateClaimException(claim.preAuthorizationId(), exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentClaimsBillingUpdateException("Claim", exception);
        }
    }

    @Override
    public Optional<Claim> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    @Override
    public boolean existsByPreAuthorizationId(UUID preAuthorizationId) {
        return repository.existsByPreAuthorizationId(preAuthorizationId);
    }

    private void mapToEntity(Claim source, ClaimJpaEntity target) {
        target.id = source.id();
        target.preAuthorizationId = source.preAuthorizationId();
        target.memberId = source.memberId();
        target.providerId = source.providerId();
        target.policyNumber = source.policyNumber();
        target.serviceCode = source.serviceCode();
        target.claimedAmount = source.claimedAmount().amount();
        target.approvedAmount = source.approvedAmount() == null ? null : source.approvedAmount().amount();
        target.currency = source.claimedAmount().currency().getCurrencyCode();
        target.status = source.status();
        target.rejectionReason = source.rejectionReason();
        target.submittedAt = source.submittedAt();
        target.reviewStartedAt = source.reviewStartedAt();
        target.decidedAt = source.decidedAt();
    }

    private Claim mapToDomain(ClaimJpaEntity source) {
        Currency currency = Currency.getInstance(source.currency);
        return Claim.rehydrate(
                source.id, source.preAuthorizationId, source.memberId, source.providerId,
                source.policyNumber, source.serviceCode,
                new Money(source.claimedAmount, currency), source.status,
                source.approvedAmount == null ? null : new Money(source.approvedAmount, currency),
                source.rejectionReason, source.submittedAt, source.reviewStartedAt, source.decidedAt);
    }
}
