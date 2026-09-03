package com.aydindemir.health.policy.infrastructure.persistence;

import com.aydindemir.health.policy.application.port.out.PolicyRepository;
import com.aydindemir.health.policy.domain.model.Coverage;
import com.aydindemir.health.policy.domain.model.Policy;
import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaPolicyRepositoryAdapter.class)
class JpaPolicyRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private PolicyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesMigrationAndRoundTripsPolicyAggregate() {
        Policy policy = policy();

        repository.save(policy);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog", Integer.class)).isEqualTo(1);
        assertThat(repository.findByPolicyNumber("pol-100"))
                .hasValueSatisfying(reloaded -> {
                    assertThat(reloaded.id()).isEqualTo(policy.id());
                    assertThat(reloaded.memberId()).isEqualTo(policy.memberId());
                    assertThat(reloaded.coverages()).singleElement().satisfies(coverage -> {
                        assertThat(coverage.serviceCode().value()).isEqualTo("IMG-MRI");
                        assertThat(coverage.remaining().amount())
                                .isEqualByComparingTo("8000.00");
                    });
                });
    }

    @Test
    void createsIndexesForOwnershipAndCaseInsensitivePolicyLookup() {
        var indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename in ('policies', 'policy_coverages')",
                String.class);

        assertThat(indexes).contains(
                "uk_policies_policy_number_lower",
                "uk_policy_coverages_service",
                "idx_policies_member_validity");
    }

    private Policy policy() {
        Currency currency = Currency.getInstance("TRY");
        return Policy.issue(
                UUID.randomUUID(), "POL-100", UUID.randomUUID(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of(new Coverage(
                        new ServiceCode("IMG-MRI"),
                        new Money(new BigDecimal("10000.00"), currency),
                        new Money(new BigDecimal("2000.00"), currency))));
    }
}
