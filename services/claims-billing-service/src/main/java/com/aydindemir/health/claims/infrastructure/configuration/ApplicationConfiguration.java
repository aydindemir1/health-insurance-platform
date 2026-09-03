package com.aydindemir.health.claims.infrastructure.configuration;

import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.application.port.out.IdentifierGenerator;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.application.usecase.ClaimsBillingApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class ApplicationConfiguration {
    @Bean
    IdentifierGenerator identifierGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionalClaimsBillingUseCases claimsBillingUseCases(
            ClaimRepository claims,
            InvoiceRepository invoices,
            ApprovedPreAuthorizationPort preAuthorizations,
            IdentifierGenerator identifiers,
            Clock clock) {
        return new TransactionalClaimsBillingUseCases(
                new ClaimsBillingApplicationService(
                        claims, invoices, preAuthorizations, identifiers, clock));
    }
}
