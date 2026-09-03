package com.aydindemir.health.authorization.adapter.in.web;

import com.aydindemir.health.authorization.application.PreAuthorizationService;
import com.aydindemir.health.authorization.application.SubmitPreAuthorizationCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PreAuthorizationService service;

    PreAuthorizationController(PreAuthorizationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_USER')")
    ResponseEntity<PreAuthorizationResponse> submit(
            @Valid @RequestBody SubmitPreAuthorizationRequest request) {
        var created = service.submit(new SubmitPreAuthorizationCommand(
                request.memberId(), request.providerId(), request.policyNumber(),
                request.diagnosisCode(), request.requestedAmount(),
                Currency.getInstance(request.currency())));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(PreAuthorizationResponse.from(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST')")
    PreAuthorizationResponse get(@PathVariable UUID id) {
        return PreAuthorizationResponse.from(service.get(id));
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('INSURANCE_SPECIALIST')")
    PreAuthorizationResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest request) {
        return PreAuthorizationResponse.from(service.approve(id, request.reason()));
    }

    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('INSURANCE_SPECIALIST')")
    PreAuthorizationResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest.Rejection request) {
        return PreAuthorizationResponse.from(service.reject(id, request.reason()));
    }
}
