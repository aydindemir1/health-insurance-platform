package com.aydindemir.health.authorization.infrastructure.persistence;

import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.PreAuthorizationSearchCriteria;
import com.aydindemir.health.authorization.application.query.SearchPreAuthorizationsQuery;
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

        assertThat(appliedChangeSets).isEqualTo(4);
        assertThat(repository.findById(submitted.id()))
                .hasValueSatisfying(reloaded -> {
                    assertThat(reloaded.memberId()).isEqualTo(submitted.memberId());
                    assertThat(reloaded.providerId()).isEqualTo(submitted.providerId());
                    assertThat(reloaded.serviceCode()).isEqualTo(submitted.serviceCode());
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

    @Test
    void filtersScopesSortsAndPaginatesWithPostgreSql() {
        jdbcTemplate.update("delete from pre_authorizations");
        UUID providerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        repository.save(newPreAuthorization(
                providerId, memberId, "POL-SEARCH", "100.00", 1));
        repository.save(newPreAuthorization(
                providerId, memberId, "POL-SEARCH", "300.00", 3));
        repository.save(newPreAuthorization(
                providerId, memberId, "POL-SEARCH", "200.00", 2));
        repository.save(newPreAuthorization(
                UUID.randomUUID(), memberId, "POL-SEARCH", "999.00", 4));
        repository.save(newPreAuthorization(
                providerId, UUID.randomUUID(), "POL-OTHER", "888.00", 5));

        var criteria = new PreAuthorizationSearchCriteria(
                providerId,
                PreAuthorizationStatus.PENDING,
                memberId,
                "pol-search",
                0,
                2,
                SearchPreAuthorizationsQuery.SortField.REQUESTED_AMOUNT,
                SearchPreAuthorizationsQuery.SortDirection.DESC);

        var result = repository.search(criteria);

        assertThat(result.content())
                .extracting(PreAuthorization::requestedAmount)
                .containsExactly(new BigDecimal("300.00"), new BigDecimal("200.00"));
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
    }

    private PreAuthorization newPreAuthorization() {
        return PreAuthorization.submit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "POL-1001",
                "IMG-MRI",
                "J18.9",
                new BigDecimal("1250.00"),
                Currency.getInstance("TRY"),
                FIXED_CLOCK);
    }

    private PreAuthorization newPreAuthorization(
            UUID providerId,
            UUID memberId,
            String policyNumber,
            String amount,
            long createdMinute) {
        return PreAuthorization.submit(
                UUID.randomUUID(),
                memberId,
                providerId,
                policyNumber,
                "IMG-MRI",
                "J18.9",
                new BigDecimal(amount),
                Currency.getInstance("TRY"),
                Clock.fixed(FIXED_CLOCK.instant().plusSeconds(createdMinute * 60),
                        ZoneOffset.UTC));
    }

    private void rollbackIfActive(jakarta.persistence.EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }
}
