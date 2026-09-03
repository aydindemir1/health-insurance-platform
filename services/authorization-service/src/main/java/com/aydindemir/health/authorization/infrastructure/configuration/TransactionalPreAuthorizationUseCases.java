package com.aydindemir.health.authorization.infrastructure.configuration;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.GetPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;
import com.aydindemir.health.authorization.application.usecase.PreAuthorizationApplicationService;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalPreAuthorizationUseCases implements
        SubmitPreAuthorizationUseCase,
        GetPreAuthorizationUseCase,
        DecidePreAuthorizationUseCase {

    private final PreAuthorizationApplicationService delegate;

    TransactionalPreAuthorizationUseCases(PreAuthorizationApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PreAuthorizationResult submit(SubmitPreAuthorizationCommand command) {
        return delegate.submit(command);
    }

    @Override
    @Transactional(readOnly = true)
    public PreAuthorizationResult get(GetPreAuthorizationQuery query) {
        return delegate.get(query);
    }

    @Override
    @Transactional
    public PreAuthorizationResult approve(DecidePreAuthorizationCommand command) {
        return delegate.approve(command);
    }

    @Override
    @Transactional
    public PreAuthorizationResult reject(DecidePreAuthorizationCommand command) {
        return delegate.reject(command);
    }
}
