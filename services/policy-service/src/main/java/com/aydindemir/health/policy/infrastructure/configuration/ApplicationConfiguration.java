package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.port.out.PolicyIdGenerator;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.application.port.out.CoverageEvaluationCache;
import com.aydindemir.health.policy.application.port.out.AuditTrail;
import com.aydindemir.health.policy.application.port.out.AuditContextProvider;
import com.aydindemir.health.policy.application.usecase.PolicyApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    PolicyIdGenerator policyIdGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    TransactionalPolicyUseCases policyUseCases(
            PolicyRepository repository,
            PolicyIdGenerator idGenerator,
            CoverageEvaluationCache coverageCache,
            AuditTrail auditTrail,
            AuditContextProvider auditContext,
            Clock clock) {
        return new TransactionalPolicyUseCases(
                new PolicyApplicationService(
                        repository, idGenerator, coverageCache, auditTrail, auditContext, clock));
    }
}
