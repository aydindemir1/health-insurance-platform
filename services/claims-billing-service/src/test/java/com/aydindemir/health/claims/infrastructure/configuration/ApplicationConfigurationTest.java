package com.aydindemir.health.claims.infrastructure.configuration;

import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.ManageInvoiceUseCase;
import com.aydindemir.health.claims.application.port.in.GetClaimsBillingUseCase;
import com.aydindemir.health.claims.application.port.in.ReviewClaimUseCase;
import com.aydindemir.health.claims.application.port.out.ApprovedPreAuthorizationPort;
import com.aydindemir.health.claims.application.port.out.ClaimRepository;
import com.aydindemir.health.claims.application.port.out.InvoiceRepository;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import com.aydindemir.health.claims.domain.model.Claim;
import com.aydindemir.health.claims.domain.model.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestInfrastructure.class);

    @Test
    void wiresAllInputPortsToOneTransactionalDecorator() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            Object create = context.getBean(CreateClaimUseCase.class);
            assertThat(create).isSameAs(context.getBean(ReviewClaimUseCase.class));
            assertThat(create).isSameAs(context.getBean(ManageInvoiceUseCase.class));
            assertThat(create).isSameAs(context.getBean(GetClaimsBillingUseCase.class));
            assertThat(AopUtils.isAopProxy(create)).isTrue();
            assertThat(AopUtils.getTargetClass(create)).isEqualTo(TransactionalClaimsBillingUseCases.class);
        });
    }

    @Test
    void createsClaimAndInvoiceInsideOneWriteTransaction() {
        runner.run(context -> {
            UUID preAuthorizationId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();
            var authorization = context.getBean(ApprovedPreAuthorizationPort.class);
            when(authorization.findById(preAuthorizationId)).thenReturn(Optional.of(
                    new ApprovedPreAuthorizationPort.PreAuthorizationSnapshot(
                            preAuthorizationId, UUID.randomUUID(), providerId, "POL-100", "IMG-MRI",
                            new BigDecimal("1000.00"), Currency.getInstance("TRY"), "APPROVED")));
            context.getBean(CreateClaimUseCase.class).create(new CreateClaimCommand(
                    new ActorContext("hospital", providerId, Set.of(ApplicationRole.HOSPITAL_USER)),
                    preAuthorizationId, "INV-100", new BigDecimal("900.00"), Currency.getInstance("TRY")));

            var manager = context.getBean(RecordingTransactionManager.class);
            assertThat(manager.readOnly).isFalse();
            assertThat(manager.commits).isEqualTo(1);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ApplicationConfiguration.class)
    static class TestInfrastructure {
        @Bean ClaimRepository claimRepository() {
            var repository = mock(ClaimRepository.class);
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, Claim.class));
            return repository;
        }
        @Bean InvoiceRepository invoiceRepository() {
            var repository = mock(InvoiceRepository.class);
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, Invoice.class));
            return repository;
        }
        @Bean ApprovedPreAuthorizationPort approvedPreAuthorizationPort() {
            return mock(ApprovedPreAuthorizationPort.class);
        }
        @Bean RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        boolean readOnly;
        int commits;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            readOnly = definition.isReadOnly();
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
