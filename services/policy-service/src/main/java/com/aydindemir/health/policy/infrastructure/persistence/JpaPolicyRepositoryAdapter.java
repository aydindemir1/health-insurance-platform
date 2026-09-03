package com.aydindemir.health.policy.infrastructure.persistence;

import com.aydindemir.health.policy.application.exception.PolicyNumberConflictException;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.domain.model.Coverage;
import com.aydindemir.health.policy.domain.model.Policy;
import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;

@Repository
class JpaPolicyRepositoryAdapter implements PolicyRepository {
    private final SpringDataPolicyRepository repository;

    JpaPolicyRepositoryAdapter(SpringDataPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Policy save(Policy policy) {
        var entity = repository.findById(policy.id()).orElseGet(PolicyJpaEntity::new);
        mapToEntity(policy, entity);
        try {
            return mapToDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new PolicyNumberConflictException(policy.policyNumber(), exception);
        }
    }

    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return repository.findByPolicyNumberIgnoreCase(policyNumber).map(this::mapToDomain);
    }

    @Override
    public boolean existsByPolicyNumber(String policyNumber) {
        return repository.existsByPolicyNumberIgnoreCase(policyNumber);
    }

    private void mapToEntity(Policy source, PolicyJpaEntity target) {
        target.id = source.id();
        target.policyNumber = source.policyNumber();
        target.memberId = source.memberId();
        target.validFrom = source.validFrom();
        target.validUntil = source.validUntil();
        target.status = source.status();
        target.coverages.clear();
        source.coverages().stream().map(this::mapCoverage).forEach(target.coverages::add);
    }

    private CoverageJpaEmbeddable mapCoverage(Coverage source) {
        var target = new CoverageJpaEmbeddable();
        target.serviceCode = source.serviceCode().value();
        target.limitAmount = source.limit().amount();
        target.usedAmount = source.used().amount();
        target.currency = source.limit().currency().getCurrencyCode();
        return target;
    }

    private Policy mapToDomain(PolicyJpaEntity source) {
        var coverages = source.coverages.stream()
                .map(coverage -> new Coverage(
                        new ServiceCode(coverage.serviceCode),
                        new Money(coverage.limitAmount, Currency.getInstance(coverage.currency)),
                        new Money(coverage.usedAmount, Currency.getInstance(coverage.currency))))
                .toList();
        return Policy.rehydrate(
                source.id, source.policyNumber, source.memberId,
                source.validFrom, source.validUntil, source.status, coverages);
    }
}
