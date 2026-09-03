package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.GetPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pre-authorizations")
class PreAuthorizationController {
    private final SubmitPreAuthorizationUseCase submitUseCase;
    private final GetPreAuthorizationUseCase getUseCase;
    private final DecidePreAuthorizationUseCase decideUseCase;
    private final AuthenticatedActorMapper actorMapper;

    PreAuthorizationController(
            SubmitPreAuthorizationUseCase submitUseCase,
            GetPreAuthorizationUseCase getUseCase,
            DecidePreAuthorizationUseCase decideUseCase,
            AuthenticatedActorMapper actorMapper) {
        this.submitUseCase = submitUseCase;
        this.getUseCase = getUseCase;
        this.decideUseCase = decideUseCase;
        this.actorMapper = actorMapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_USER')")
    ResponseEntity<PreAuthorizationResponse> submit(
            @Valid @RequestBody SubmitPreAuthorizationRequest request,
            JwtAuthenticationToken authentication) {
        var created = submitUseCase.submit(new SubmitPreAuthorizationCommand(
                actorMapper.from(authentication), request.memberId(), request.policyNumber(),
                request.diagnosisCode(), request.requestedAmount(),
                Currency.getInstance(request.currency())));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(PreAuthorizationResponse.from(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'SYSTEM_ADMIN')")
    PreAuthorizationResponse get(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication) {
        var result = getUseCase.get(new GetPreAuthorizationQuery(
                id, actorMapper.from(authentication)));
        return PreAuthorizationResponse.from(result);
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('INSURANCE_SPECIALIST')")
    PreAuthorizationResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest request,
            JwtAuthenticationToken authentication) {
        var command = new DecidePreAuthorizationCommand(
                id, request.reason(), actorMapper.from(authentication));
        return PreAuthorizationResponse.from(decideUseCase.approve(command));
    }

    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('INSURANCE_SPECIALIST')")
    PreAuthorizationResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest.Rejection request,
            JwtAuthenticationToken authentication) {
        var command = new DecidePreAuthorizationCommand(
                id, request.reason(), actorMapper.from(authentication));
        return PreAuthorizationResponse.from(decideUseCase.reject(command));
    }
}
