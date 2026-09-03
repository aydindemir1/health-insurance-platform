package com.aydindemir.health.authorization.adapter.out.persistence;

import com.aydindemir.health.authorization.application.PreAuthorizationRepository;
import com.aydindemir.health.authorization.domain.PreAuthorization;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPreAuthorizationRepositoryAdapter implements PreAuthorizationRepository {
    private final SpringDataPreAuthorizationRepository repository;

    JpaPreAuthorizationRepositoryAdapter(SpringDataPreAuthorizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public PreAuthorization save(PreAuthorization domain) {
        var entity = repository.findById(domain.id())
                .orElseGet(PreAuthorizationJpaEntity::new);
        mapToEntity(domain, entity);
        return mapToDomain(repository.save(entity));
    }

    @Override
    public Optional<PreAuthorization> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    private void mapToEntity(PreAuthorization source, PreAuthorizationJpaEntity target) {
        target.id = source.id();
        target.memberId = source.memberId();
        target.providerId = source.providerId();
        target.policyNumber = source.policyNumber();
        target.diagnosisCode = source.diagnosisCode();
        target.requestedAmount = source.requestedAmount();
        target.currency = source.currency().getCurrencyCode();
        target.status = source.status();
        target.decisionReason = source.decisionReason();
        target.createdAt = source.createdAt();
        target.decidedAt = source.decidedAt();
    }

    private PreAuthorization mapToDomain(PreAuthorizationJpaEntity entity) {
        return PreAuthorization.rehydrate(
                entity.id, entity.memberId, entity.providerId, entity.policyNumber,
                entity.diagnosisCode, entity.requestedAmount,
                Currency.getInstance(entity.currency), entity.status,
                entity.decisionReason, entity.createdAt, entity.decidedAt);
    }
}
