package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.port.out.PolicyIdGenerator;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.application.usecase.PolicyApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class ApplicationConfiguration {
    @Bean
    PolicyIdGenerator policyIdGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    TransactionalPolicyUseCases policyUseCases(
            PolicyRepository repository,
            PolicyIdGenerator idGenerator) {
        return new TransactionalPolicyUseCases(
                new PolicyApplicationService(repository, idGenerator));
    }
}
