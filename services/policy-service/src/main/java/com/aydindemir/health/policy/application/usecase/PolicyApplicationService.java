package com.aydindemir.health.policy.application.usecase;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.dto.PolicyResult;
import com.aydindemir.health.policy.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.policy.application.exception.PolicyNumberConflictException;
import com.aydindemir.health.policy.application.mapper.PolicyResultMapper;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.EvaluateCoverageUseCase;
import com.aydindemir.health.policy.application.port.out.PolicyIdGenerator;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import com.aydindemir.health.policy.domain.model.Coverage;
import com.aydindemir.health.policy.domain.model.Policy;
import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;

import java.math.BigDecimal;
import java.util.Objects;

public final class PolicyApplicationService implements CreatePolicyUseCase, EvaluateCoverageUseCase {
    private final PolicyRepository repository;
    private final PolicyIdGenerator idGenerator;

    public PolicyApplicationService(
            PolicyRepository repository,
            PolicyIdGenerator idGenerator) {
        this.repository = Objects.requireNonNull(repository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public PolicyResult create(CreatePolicyCommand command) {
        Objects.requireNonNull(command);
        requirePolicyManager(command.actor());
        if (repository.existsByPolicyNumber(command.policyNumber())) {
            throw new PolicyNumberConflictException(command.policyNumber());
        }
        var coverages = command.coverages().stream()
                .map(definition -> new Coverage(
                        new ServiceCode(definition.serviceCode()),
                        Money.positive(definition.limit(), definition.currency()),
                        new Money(BigDecimal.ZERO, definition.currency())))
                .toList();
        var policy = Policy.issue(
                idGenerator.generate(), command.policyNumber(), command.memberId(),
                command.validFrom(), command.validUntil(), coverages);
        return PolicyResultMapper.toResult(repository.save(policy));
    }

    @Override
    public CoverageEvaluationResult evaluate(EvaluateCoverageCommand command) {
        Objects.requireNonNull(command);
        requireOperationsRole(command.actor());
        return repository.findByPolicyNumber(command.policyNumber())
                .map(policy -> PolicyResultMapper.toResult(policy, policy.evaluate(
                        command.memberId(), new ServiceCode(command.serviceCode()),
                        Money.positive(command.requestedAmount(), command.currency()),
                        command.serviceDate())))
                .orElseGet(CoverageEvaluationResult::policyNotFound);
    }

    private void requirePolicyManager(ActorContext actor) {
        if (!actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                && !actor.hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException(
                    "Policy management requires INSURANCE_SPECIALIST or SYSTEM_ADMIN");
        }
    }

    private void requireOperationsRole(ActorContext actor) {
        if (!actor.hasRole(ApplicationRole.HOSPITAL_USER)
                && !actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                && !actor.hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException(
                    "Coverage evaluation requires an operations role");
        }
    }
}
