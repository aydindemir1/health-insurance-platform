package com.aydindemir.health.authorization.infrastructure.configuration;

import com.aydindemir.health.authorization.application.port.out.PreAuthorizationIdGenerator;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.port.out.CoverageVerificationPort;
import com.aydindemir.health.authorization.application.port.out.IntegrationEventOutbox;
import com.aydindemir.health.authorization.application.port.out.NotificationTaskOutbox;
import com.aydindemir.health.authorization.application.port.out.AuditContextProvider;
import com.aydindemir.health.authorization.application.port.out.AuditTrail;
import com.aydindemir.health.authorization.application.port.out.AuditRecordQuery;
import com.aydindemir.health.authorization.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.authorization.application.usecase.AuditQueryService;
import com.aydindemir.health.authorization.application.usecase.PreAuthorizationApplicationService;
import com.aydindemir.health.authorization.application.usecase.SearchProjectionExportService;
import com.aydindemir.health.authorization.application.port.in.ExportSearchProjectionsUseCase;
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
            CoverageVerificationPort coverageVerification,
            IntegrationEventOutbox eventOutbox,
            NotificationTaskOutbox notificationTaskOutbox,
            AuditTrail auditTrail,
            AuditContextProvider auditContext,
            Clock clock) {
        var applicationService = new PreAuthorizationApplicationService(
                repository, idGenerator, coverageVerification, eventOutbox,
                notificationTaskOutbox, auditTrail, auditContext, clock);
        return new TransactionalPreAuthorizationUseCases(applicationService);
    }

    @Bean
    SearchAuditRecordsUseCase auditQuery(AuditRecordQuery records) {
        return new TransactionalAuditQuery(new AuditQueryService(records));
    }

    @Bean
    ExportSearchProjectionsUseCase searchProjectionExporter(PreAuthorizationRepository repository) {
        return new SearchProjectionExportService(repository);
    }
}
