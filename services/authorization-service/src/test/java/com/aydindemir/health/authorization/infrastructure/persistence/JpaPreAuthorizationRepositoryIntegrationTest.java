package com.aydindemir.health.authorization.infrastructure.persistence;

import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaPreAuthorizationRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaPreAuthorizationRepositoryIntegrationTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private PreAuthorizationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void appliesLiquibaseMigrationAndRoundTripsTheAggregate() {
        Integer appliedChangeSets = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog",
                Integer.class);
        PreAuthorization submitted = newPreAuthorization();

        repository.save(submitted);

        assertThat(appliedChangeSets).isEqualTo(2);
        assertThat(repository.findById(submitted.id()))
                .hasValueSatisfying(reloaded -> {
                    assertThat(reloaded.memberId()).isEqualTo(submitted.memberId());
                    assertThat(reloaded.providerId()).isEqualTo(submitted.providerId());
                    assertThat(reloaded.requestedAmount())
                            .isEqualByComparingTo(submitted.requestedAmount());
                    assertThat(reloaded.status()).isEqualTo(PreAuthorizationStatus.PENDING);
                });
    }

    @Test
    void rejectsAStaleConcurrentUpdateUsingTheVersionColumn() {
        PreAuthorization submitted = repository.save(newPreAuthorization());
        var firstEntityManager = entityManagerFactory.createEntityManager();
        var secondEntityManager = entityManagerFactory.createEntityManager();

        try {
            firstEntityManager.getTransaction().begin();
            secondEntityManager.getTransaction().begin();
            var firstCopy = firstEntityManager.find(
                    PreAuthorizationJpaEntity.class, submitted.id());
            var staleCopy = secondEntityManager.find(
                    PreAuthorizationJpaEntity.class, submitted.id());

            firstCopy.status = PreAuthorizationStatus.APPROVED;
            firstEntityManager.getTransaction().commit();

            staleCopy.status = PreAuthorizationStatus.REJECTED;
            assertThatThrownBy(secondEntityManager.getTransaction()::commit)
                    .isInstanceOfAny(RollbackException.class, OptimisticLockException.class);
        } finally {
            rollbackIfActive(firstEntityManager);
            rollbackIfActive(secondEntityManager);
            firstEntityManager.close();
            secondEntityManager.close();
        }
    }

    private PreAuthorization newPreAuthorization() {
        return PreAuthorization.submit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "POL-1001",
                "J18.9",
                new BigDecimal("1250.00"),
                Currency.getInstance("TRY"),
                FIXED_CLOCK);
    }

    private void rollbackIfActive(jakarta.persistence.EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }
}
