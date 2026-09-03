package com.aydindemir.health.policy.application.usecase;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.policy.application.exception.PolicyNumberConflictException;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import com.aydindemir.health.policy.domain.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyApplicationServiceTest {
    private static final UUID POLICY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Currency TRY = Currency.getInstance("TRY");

    private PolicyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyApplicationService(new InMemoryPolicyRepository(), () -> POLICY_ID);
    }

    @Test
    void createsActivePolicyWithUnusedCoverage() {
        var result = service.create(createCommand(specialist()));

        assertThat(result.id()).isEqualTo(POLICY_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.coverages()).singleElement().satisfies(coverage -> {
            assertThat(coverage.serviceCode()).isEqualTo("IMG-MRI");
            assertThat(coverage.used()).isEqualByComparingTo("0");
            assertThat(coverage.remaining()).isEqualByComparingTo("10000.00");
        });
    }

    @Test
    void preventsHospitalUserFromManagingPolicies() {
        assertThatThrownBy(() -> service.create(createCommand(hospital())))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    @Test
    void rejectsDuplicatePolicyNumber() {
        service.create(createCommand(specialist()));

        assertThatThrownBy(() -> service.create(createCommand(specialist())))
                .isInstanceOf(PolicyNumberConflictException.class);
    }

    @Test
    void reportsMissingPolicyAsBusinessDenial() {
        CoverageEvaluationResult result = service.evaluate(new EvaluateCoverageCommand(
                hospital(), "POL-MISSING", MEMBER_ID, "IMG-MRI",
                new BigDecimal("100.00"), TRY, LocalDate.parse("2026-09-03")));

        assertThat(result.eligible()).isFalse();
        assertThat(result.code()).isEqualTo("POLICY_NOT_FOUND");
    }

    private CreatePolicyCommand createCommand(ActorContext actor) {
        return new CreatePolicyCommand(
                actor, "POL-100", MEMBER_ID,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of(new CreatePolicyCommand.CoverageDefinition(
                        "IMG-MRI", new BigDecimal("10000.00"), TRY)));
    }

    private ActorContext specialist() {
        return new ActorContext(
                "specialist", Set.of(ApplicationRole.INSURANCE_SPECIALIST));
    }

    private ActorContext hospital() {
        return new ActorContext("hospital", Set.of(ApplicationRole.HOSPITAL_USER));
    }

    private static final class InMemoryPolicyRepository implements PolicyRepository {
        private final Map<String, Policy> policies = new HashMap<>();

        @Override
        public Policy save(Policy policy) {
            policies.put(policy.policyNumber().toUpperCase(), policy);
            return policy;
        }

        @Override
        public Optional<Policy> findByPolicyNumber(String policyNumber) {
            return Optional.ofNullable(policies.get(policyNumber.toUpperCase()));
        }

        @Override
        public boolean existsByPolicyNumber(String policyNumber) {
            return policies.containsKey(policyNumber.toUpperCase());
        }
    }
}
