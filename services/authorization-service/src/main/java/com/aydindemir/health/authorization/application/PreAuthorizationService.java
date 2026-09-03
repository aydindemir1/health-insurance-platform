package com.aydindemir.health.authorization.application;

import com.aydindemir.health.authorization.domain.PreAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class PreAuthorizationService {
    private final PreAuthorizationRepository repository;
    private final Clock clock;

    public PreAuthorizationService(PreAuthorizationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PreAuthorization submit(SubmitPreAuthorizationCommand command) {
        var preAuthorization = PreAuthorization.submit(
                command.memberId(), command.providerId(), command.policyNumber(),
                command.diagnosisCode(), command.requestedAmount(), command.currency(), clock);
        return repository.save(preAuthorization);
    }

    @Transactional(readOnly = true)
    public PreAuthorization get(UUID id) {
        return find(id);
    }

    @Transactional
    public PreAuthorization approve(UUID id, String reason) {
        var preAuthorization = find(id);
        preAuthorization.approve(reason, clock);
        return repository.save(preAuthorization);
    }

    @Transactional
    public PreAuthorization reject(UUID id, String reason) {
        var preAuthorization = find(id);
        preAuthorization.reject(reason, clock);
        return repository.save(preAuthorization);
    }

    private PreAuthorization find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PreAuthorizationNotFoundException(id));
    }
}
