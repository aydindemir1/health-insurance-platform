package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.exception.PreAuthorizationNotFoundException;
import com.aydindemir.health.authorization.application.exception.PreAuthorizationStateConflictException;
import com.aydindemir.health.authorization.application.mapper.PreAuthorizationResultMapper;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.GetPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationIdGenerator;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
import com.aydindemir.health.authorization.domain.exception.InvalidPreAuthorizationStateException;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class PreAuthorizationApplicationService implements
        SubmitPreAuthorizationUseCase,
        GetPreAuthorizationUseCase,
        DecidePreAuthorizationUseCase {

    private final PreAuthorizationRepository repository;
    private final PreAuthorizationIdGenerator idGenerator;
    private final Clock clock;

    public PreAuthorizationApplicationService(
            PreAuthorizationRepository repository,
            PreAuthorizationIdGenerator idGenerator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PreAuthorizationResult submit(SubmitPreAuthorizationCommand command) {
        Objects.requireNonNull(command);
        requireRole(command.actor(), ApplicationRole.HOSPITAL_USER);
        UUID providerId = requireProvider(command.actor());
        var preAuthorization = PreAuthorization.submit(
                idGenerator.generate(), command.memberId(), providerId,
                command.policyNumber(), command.diagnosisCode(),
                command.requestedAmount(), command.currency(), clock);
        return PreAuthorizationResultMapper.toResult(repository.save(preAuthorization));
    }

    @Override
    public PreAuthorizationResult get(GetPreAuthorizationQuery query) {
        Objects.requireNonNull(query);
        var preAuthorization = find(query.id());
        assertCanView(query.actor(), preAuthorization.providerId());
        return PreAuthorizationResultMapper.toResult(preAuthorization);
    }

    @Override
    public PreAuthorizationResult approve(DecidePreAuthorizationCommand command) {
        requireSpecialist(command);
        var preAuthorization = find(command.id());
        try {
            preAuthorization.approve(command.reason(), clock);
        } catch (InvalidPreAuthorizationStateException exception) {
            throw new PreAuthorizationStateConflictException(exception.getMessage(), exception);
        }
        return PreAuthorizationResultMapper.toResult(repository.save(preAuthorization));
    }

    @Override
    public PreAuthorizationResult reject(DecidePreAuthorizationCommand command) {
        requireSpecialist(command);
        var preAuthorization = find(command.id());
        try {
            preAuthorization.reject(command.reason(), clock);
        } catch (InvalidPreAuthorizationStateException exception) {
            throw new PreAuthorizationStateConflictException(exception.getMessage(), exception);
        }
        return PreAuthorizationResultMapper.toResult(repository.save(preAuthorization));
    }

    private PreAuthorization find(UUID id) {
        return repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new PreAuthorizationNotFoundException(id));
    }

    private void assertCanView(ActorContext actor, UUID ownerProviderId) {
        if (actor.hasRole(ApplicationRole.INSURANCE_SPECIALIST)
                || actor.hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            return;
        }
        requireRole(actor, ApplicationRole.HOSPITAL_USER);
        if (!ownerProviderId.equals(requireProvider(actor))) {
            throw new ApplicationAccessDeniedException(
                    "Hospital users can only view their own provider's pre-authorizations");
        }
    }

    private void requireSpecialist(DecidePreAuthorizationCommand command) {
        Objects.requireNonNull(command);
        requireRole(command.actor(), ApplicationRole.INSURANCE_SPECIALIST);
    }

    private void requireRole(ActorContext actor, ApplicationRole role) {
        Objects.requireNonNull(actor);
        if (!actor.hasRole(role)) {
            throw new ApplicationAccessDeniedException("Required role: " + role);
        }
    }

    private UUID requireProvider(ActorContext actor) {
        if (actor.providerId() == null) {
            throw new ApplicationAccessDeniedException(
                    "A provider identity is required for hospital operations");
        }
        return actor.providerId();
    }
}
