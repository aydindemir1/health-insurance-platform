package com.aydindemir.health.authorization.infrastructure.configuration;

import com.aydindemir.health.authorization.application.port.out.PreAuthorizationIdGenerator;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.usecase.PreAuthorizationApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    PreAuthorizationIdGenerator preAuthorizationIdGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    TransactionalPreAuthorizationUseCases preAuthorizationUseCases(
            PreAuthorizationRepository repository,
            PreAuthorizationIdGenerator idGenerator,
            Clock clock) {
        var applicationService = new PreAuthorizationApplicationService(
                repository, idGenerator, clock);
        return new TransactionalPreAuthorizationUseCases(applicationService);
    }
}
