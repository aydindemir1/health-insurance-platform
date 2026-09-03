package com.aydindemir.health.authorization.infrastructure.persistence;

import com.aydindemir.health.authorization.application.exception.ConcurrentPreAuthorizationUpdateException;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
import com.aydindemir.health.authorization.domain.model.PreAuthorizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaPreAuthorizationRepositoryAdapterTest {
    private static final UUID PRE_AUTHORIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SpringDataPreAuthorizationRepository repository;
    private JpaPreAuthorizationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataPreAuthorizationRepository.class);
        adapter = new JpaPreAuthorizationRepositoryAdapter(repository);
        when(repository.saveAndFlush(any(PreAuthorizationJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void mapsNewDomainAggregateToJpaEntityAndBack() {
        when(repository.findById(PRE_AUTHORIZATION_ID)).thenReturn(Optional.empty());

        var saved = adapter.save(newPendingPreAuthorization());

        var captor = ArgumentCaptor.forClass(PreAuthorizationJpaEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.id).isEqualTo(PRE_AUTHORIZATION_ID);
        assertThat(entity.memberId).isEqualTo(MEMBER_ID);
        assertThat(entity.providerId).isEqualTo(PROVIDER_ID);
        assertThat(entity.policyNumber).isEqualTo("POL-100");
        assertThat(entity.diagnosisCode).isEqualTo("J18.9");
        assertThat(entity.requestedAmount).isEqualByComparingTo("1250.00");
        assertThat(entity.currency).isEqualTo("TRY");
        assertThat(entity.status).isEqualTo(PreAuthorizationStatus.PENDING);
        assertThat(entity.createdAt).isEqualTo(NOW);

        assertThat(saved.id()).isEqualTo(PRE_AUTHORIZATION_ID);
        assertThat(saved.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(saved.status()).isEqualTo(PreAuthorizationStatus.PENDING);
    }

    @Test
    void reusesManagedEntitySoOptimisticLockVersionIsPreserved() {
        var existingEntity = pendingEntity();
        existingEntity.version = 7L;
        when(repository.findById(PRE_AUTHORIZATION_ID)).thenReturn(Optional.of(existingEntity));
        var approved = newPendingPreAuthorization();
        approved.approve("Coverage verified", CLOCK);

        adapter.save(approved);

        var captor = ArgumentCaptor.forClass(PreAuthorizationJpaEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(existingEntity);
        assertThat(existingEntity.version).isEqualTo(7L);
        assertThat(existingEntity.status).isEqualTo(PreAuthorizationStatus.APPROVED);
        assertThat(existingEntity.decisionReason).isEqualTo("Coverage verified");
        assertThat(existingEntity.decidedAt).isEqualTo(NOW);
    }

    @Test
    void rehydratesDomainAggregateFromJpaEntity() {
        var entity = pendingEntity();
        entity.status = PreAuthorizationStatus.REJECTED;
        entity.decisionReason = "Policy limit exceeded";
        entity.decidedAt = NOW.plusSeconds(60);
        when(repository.findById(PRE_AUTHORIZATION_ID)).thenReturn(Optional.of(entity));

        var found = adapter.findById(PRE_AUTHORIZATION_ID);

        assertThat(found).isPresent();
        var domain = found.orElseThrow();
        assertThat(domain.id()).isEqualTo(PRE_AUTHORIZATION_ID);
        assertThat(domain.memberId()).isEqualTo(MEMBER_ID);
        assertThat(domain.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(domain.policyNumber()).isEqualTo("POL-100");
        assertThat(domain.diagnosisCode()).isEqualTo("J18.9");
        assertThat(domain.requestedAmount()).isEqualByComparingTo("1250.00");
        assertThat(domain.currency()).isEqualTo(Currency.getInstance("TRY"));
        assertThat(domain.status()).isEqualTo(PreAuthorizationStatus.REJECTED);
        assertThat(domain.decisionReason()).isEqualTo("Policy limit exceeded");
        assertThat(domain.createdAt()).isEqualTo(NOW);
        assertThat(domain.decidedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void translatesOptimisticLockFailureToApplicationConflict() {
        when(repository.findById(PRE_AUTHORIZATION_ID))
                .thenReturn(Optional.of(pendingEntity()));
        when(repository.saveAndFlush(any(PreAuthorizationJpaEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        assertThatThrownBy(() -> adapter.save(newPendingPreAuthorization()))
                .isInstanceOf(ConcurrentPreAuthorizationUpdateException.class)
                .hasMessageContaining(PRE_AUTHORIZATION_ID.toString())
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    }

    private PreAuthorization newPendingPreAuthorization() {
        return PreAuthorization.submit(
                PRE_AUTHORIZATION_ID,
                MEMBER_ID,
                PROVIDER_ID,
                "POL-100",
                "J18.9",
                new BigDecimal("1250.00"),
                Currency.getInstance("TRY"),
                CLOCK);
    }

    private PreAuthorizationJpaEntity pendingEntity() {
        var entity = new PreAuthorizationJpaEntity();
        entity.id = PRE_AUTHORIZATION_ID;
        entity.memberId = MEMBER_ID;
        entity.providerId = PROVIDER_ID;
        entity.policyNumber = "POL-100";
        entity.diagnosisCode = "J18.9";
        entity.requestedAmount = new BigDecimal("1250.00");
        entity.currency = "TRY";
        entity.status = PreAuthorizationStatus.PENDING;
        entity.createdAt = NOW;
        return entity;
    }
}
