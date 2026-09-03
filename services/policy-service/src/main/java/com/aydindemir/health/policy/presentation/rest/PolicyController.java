package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.EvaluateCoverageUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;

@RestController
@RequestMapping("/api/v1")
class PolicyController {
    private final CreatePolicyUseCase createPolicy;
    private final EvaluateCoverageUseCase evaluateCoverage;
    private final AuthenticatedActorMapper actorMapper;

    PolicyController(
            CreatePolicyUseCase createPolicy,
            EvaluateCoverageUseCase evaluateCoverage,
            AuthenticatedActorMapper actorMapper) {
        this.createPolicy = createPolicy;
        this.evaluateCoverage = evaluateCoverage;
        this.actorMapper = actorMapper;
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAnyRole('INSURANCE_SPECIALIST', 'SYSTEM_ADMIN')")
    ResponseEntity<PolicyResponse> create(
            @Valid @RequestBody CreatePolicyRequest request,
            JwtAuthenticationToken authentication) {
        var coverageDefinitions = request.coverages().stream()
                .map(coverage -> new CreatePolicyCommand.CoverageDefinition(
                        coverage.serviceCode(), coverage.limit(),
                        Currency.getInstance(coverage.currency())))
                .toList();
        var created = createPolicy.create(new CreatePolicyCommand(
                actorMapper.from(authentication), request.policyNumber(), request.memberId(),
                request.validFrom(), request.validUntil(), coverageDefinitions));
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/policies/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(PolicyResponse.from(created));
    }

    @PostMapping("/coverage-evaluations")
    @PreAuthorize("hasAnyRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'SYSTEM_ADMIN')")
    CoverageEvaluationResponse evaluate(
            @Valid @RequestBody CoverageEvaluationRequest request,
            JwtAuthenticationToken authentication) {
        var result = evaluateCoverage.evaluate(new EvaluateCoverageCommand(
                actorMapper.from(authentication), request.policyNumber(), request.memberId(),
                request.serviceCode(), request.requestedAmount(),
                Currency.getInstance(request.currency()),
                request.serviceDate()));
        return CoverageEvaluationResponse.from(result);
    }
}
