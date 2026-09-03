package com.aydindemir.health.policy.infrastructure.configuration;

import com.aydindemir.health.policy.application.command.CreatePolicyCommand;
import com.aydindemir.health.policy.application.command.EvaluateCoverageCommand;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.EvaluateCoverageUseCase;
import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
import com.aydindemir.health.policy.domain.model.Policy;
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
import java.time.LocalDate;
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
    void wiresBothInputPortsToTheTransactionalDecorator() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            Object create = context.getBean(CreatePolicyUseCase.class);
            Object evaluate = context.getBean(EvaluateCoverageUseCase.class);

            assertThat(create).isSameAs(evaluate);
            assertThat(AopUtils.isAopProxy(create)).isTrue();
            assertThat(AopUtils.getTargetClass(create)).isEqualTo(TransactionalPolicyUseCases.class);
        });
    }

    @Test
    void appliesWriteAndReadOnlyTransactionsAtTheInfrastructureBoundary() {
        contextRunner.run(context -> {
            var create = context.getBean(CreatePolicyUseCase.class);
            var evaluate = context.getBean(EvaluateCoverageUseCase.class);
            var transactionManager = context.getBean(RecordingTransactionManager.class);
            var actor = new ActorContext(
                    "specialist", Set.of(ApplicationRole.INSURANCE_SPECIALIST));
            UUID memberId = UUID.randomUUID();
            create.create(new CreatePolicyCommand(
                    actor, "POL-100", memberId,
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                    List.of(new CreatePolicyCommand.CoverageDefinition(
                            "IMG-MRI", new BigDecimal("10000.00"),
                            Currency.getInstance("TRY")))));
            evaluate.evaluate(new EvaluateCoverageCommand(
                    actor, "POL-100", memberId, "IMG-MRI", new BigDecimal("1250.00"),
                    Currency.getInstance("TRY"), LocalDate.parse("2026-09-03")));

            assertThat(transactionManager.readOnlyTransactions())
                    .containsExactly(false, true);
            assertThat(transactionManager.committedTransactions()).isEqualTo(2);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ApplicationConfiguration.class)
    static class TestInfrastructureConfiguration {
        @Bean
        PolicyRepository policyRepository() {
            return new InMemoryPolicyRepository();
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
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
