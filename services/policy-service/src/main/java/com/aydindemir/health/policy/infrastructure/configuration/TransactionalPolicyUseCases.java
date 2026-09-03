package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.dto.PolicyResult;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.EvaluateCoverageUseCase;
import com.aydindemir.health.policy.application.usecase.PolicyApplicationService;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalPolicyUseCases implements CreatePolicyUseCase, EvaluateCoverageUseCase {
    private final PolicyApplicationService delegate;

    TransactionalPolicyUseCases(PolicyApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PolicyResult create(CreatePolicyCommand command) {
        return delegate.create(command);
    }

    @Override
    @Transactional(readOnly = true)
    public CoverageEvaluationResult evaluate(EvaluateCoverageCommand command) {
        return delegate.evaluate(command);
    }
}
