package com.aydindemir.health.authorization.infrastructure.configuration;

import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.GetPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SearchPreAuthorizationsUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;
import com.aydindemir.health.authorization.application.query.PreAuthorizationSearchCriteria;
import com.aydindemir.health.authorization.application.query.SearchPreAuthorizationsQuery;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
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
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestInfrastructureConfiguration.class);

    @Test
    void wiresAllInputPortsToOneTransactionalDecorator() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            Object submit = context.getBean(SubmitPreAuthorizationUseCase.class);
            Object get = context.getBean(GetPreAuthorizationUseCase.class);
            Object search = context.getBean(SearchPreAuthorizationsUseCase.class);
            Object decide = context.getBean(DecidePreAuthorizationUseCase.class);

            assertThat(submit).isSameAs(get).isSameAs(search).isSameAs(decide);
            assertThat(AopUtils.isAopProxy(submit)).isTrue();
            assertThat(AopUtils.getTargetClass(submit))
                    .isEqualTo(TransactionalPreAuthorizationUseCases.class);
        });
    }

    @Test
    void appliesWriteAndReadOnlyTransactionsAtTheInfrastructureBoundary() {
        contextRunner.run(context -> {
            var submit = context.getBean(SubmitPreAuthorizationUseCase.class);
            var get = context.getBean(GetPreAuthorizationUseCase.class);
            var search = context.getBean(SearchPreAuthorizationsUseCase.class);
            var transactionManager = context.getBean(RecordingTransactionManager.class);
            UUID providerId = UUID.randomUUID();
            var actor = new ActorContext(
                    "hospital-user", providerId, Set.of(ApplicationRole.HOSPITAL_USER));

            var submitted = submit.submit(new SubmitPreAuthorizationCommand(
                    actor,
                    UUID.randomUUID(),
                    "POL-1001",
                    "J18.9",
                    new BigDecimal("1250.00"),
                    Currency.getInstance("TRY")));
            get.get(new GetPreAuthorizationQuery(submitted.id(), actor));
            search.search(SearchPreAuthorizationsQuery.fromRequest(
                    actor, null, null, null, 0, 20, "createdAt", "desc"));

            assertThat(transactionManager.readOnlyTransactions())
                    .containsExactly(false, true, true);
            assertThat(transactionManager.committedTransactions()).isEqualTo(3);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ApplicationConfiguration.class)
    static class TestInfrastructureConfiguration {
        @Bean
        PreAuthorizationRepository preAuthorizationRepository() {
            return new InMemoryPreAuthorizationRepository();
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    private static final class InMemoryPreAuthorizationRepository
            implements PreAuthorizationRepository {
        private final Map<UUID, PreAuthorization> records = new HashMap<>();

        @Override
        public PreAuthorization save(PreAuthorization preAuthorization) {
            records.put(preAuthorization.id(), preAuthorization);
            return preAuthorization;
        }

        @Override
        public Optional<PreAuthorization> findById(UUID id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public PageResult<PreAuthorization> search(PreAuthorizationSearchCriteria criteria) {
            return new PageResult<>(List.copyOf(records.values()),
                    criteria.page(), criteria.size(), records.size(),
                    records.isEmpty() ? 0 : 1);
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<Boolean> readOnlyTransactions = new ArrayList<>();
        private int committedTransactions;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            readOnlyTransactions.add(definition.isReadOnly());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committedTransactions++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No resources are enlisted in this deliberately limited context test.
        }

        List<Boolean> readOnlyTransactions() {
            return List.copyOf(readOnlyTransactions);
        }

        int committedTransactions() {
            return committedTransactions;
        }
    }
}
