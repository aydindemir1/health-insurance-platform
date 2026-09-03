package com.aydindemir.health.authorization.infrastructure.persistence;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.exception.ConcurrentPreAuthorizationUpdateException;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.PreAuthorizationSearchCriteria;
import com.aydindemir.health.authorization.application.query.SearchPreAuthorizationsQuery;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;
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
        try {
            return mapToDomain(repository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException exception) {
            throw new ConcurrentPreAuthorizationUpdateException(domain.id(), exception);
        }
    }

    @Override
    public Optional<PreAuthorization> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    @Override
    public PageResult<PreAuthorization> search(PreAuthorizationSearchCriteria criteria) {
        var direction = criteria.direction()
                == SearchPreAuthorizationsQuery.SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        var sort = Sort.by(direction, persistenceProperty(criteria))
                .and(Sort.by(Sort.Direction.ASC, "id"));
        var pageable = PageRequest.of(criteria.page(), criteria.size(), sort);
        var result = repository.findAll(specification(criteria), pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::mapToDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Specification<PreAuthorizationJpaEntity> specification(
            PreAuthorizationSearchCriteria criteria) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (criteria.providerId() != null) {
                predicates.add(builder.equal(root.get("providerId"), criteria.providerId()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.memberId() != null) {
                predicates.add(builder.equal(root.get("memberId"), criteria.memberId()));
            }
            if (criteria.policyNumber() != null) {
                predicates.add(builder.equal(
                        builder.lower(root.get("policyNumber")),
                        criteria.policyNumber().toLowerCase(Locale.ROOT)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String persistenceProperty(PreAuthorizationSearchCriteria criteria) {
        return switch (criteria.sortBy()) {
            case CREATED_AT -> "createdAt";
            case REQUESTED_AMOUNT -> "requestedAmount";
            case STATUS -> "status";
        };
    }

    private void mapToEntity(PreAuthorization source, PreAuthorizationJpaEntity target) {
        target.id = source.id();
        target.memberId = source.memberId();
        target.providerId = source.providerId();
        target.policyNumber = source.policyNumber();
        target.serviceCode = source.serviceCode();
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
                entity.serviceCode,
                entity.diagnosisCode, entity.requestedAmount,
                Currency.getInstance(entity.currency), entity.status,
                entity.decisionReason, entity.createdAt, entity.decidedAt);
    }
}
