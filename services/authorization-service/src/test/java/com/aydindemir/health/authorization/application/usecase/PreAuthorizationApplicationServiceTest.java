package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.command.DecidePreAuthorizationCommand;
import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.exception.PreAuthorizationStateConflictException;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.GetPreAuthorizationQuery;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import com.aydindemir.health.authorization.domain.model.PreAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreAuthorizationApplicationServiceTest {
    private static final UUID PRE_AUTHORIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryPreAuthorizationRepository repository;
    private PreAuthorizationApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPreAuthorizationRepository();
        service = new PreAuthorizationApplicationService(
                repository, () -> PRE_AUTHORIZATION_ID, CLOCK);
    }

    @Test
    void derivesProviderOwnershipFromAuthenticatedActor() {
        var result = service.submit(submitCommand(hospitalActor(PROVIDER_ID)));

        assertThat(result.id()).isEqualTo(PRE_AUTHORIZATION_ID);
        assertThat(result.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void requiresProviderIdentityWhenHospitalSubmits() {
        var actorWithoutProvider = new ActorContext(
                "hospital-user", null, Set.of(ApplicationRole.HOSPITAL_USER));

        assertThatThrownBy(() -> service.submit(submitCommand(actorWithoutProvider)))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("provider identity");
    }

    @Test
    void allowsHospitalToViewOwnProviderRequest() {
        service.submit(submitCommand(hospitalActor(PROVIDER_ID)));

        var result = service.get(new GetPreAuthorizationQuery(
                PRE_AUTHORIZATION_ID, hospitalActor(PROVIDER_ID)));

        assertThat(result.providerId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    void preventsHospitalFromViewingAnotherProvidersRequest() {
        service.submit(submitCommand(hospitalActor(PROVIDER_ID)));

        assertThatThrownBy(() -> service.get(new GetPreAuthorizationQuery(
                PRE_AUTHORIZATION_ID, hospitalActor(OTHER_PROVIDER_ID))))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("own provider");
    }

    @Test
    void allowsInsuranceSpecialistToViewAnyProviderRequest() {
        service.submit(submitCommand(hospitalActor(PROVIDER_ID)));

        var result = service.get(new GetPreAuthorizationQuery(
                PRE_AUTHORIZATION_ID, specialistActor()));

        assertThat(result.providerId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    void enforcesDecisionRoleInsideApplicationLayer() {
        service.submit(submitCommand(hospitalActor(PROVIDER_ID)));
        var command = new DecidePreAuthorizationCommand(
                PRE_AUTHORIZATION_ID, "Coverage verified", hospitalActor(PROVIDER_ID));

        assertThatThrownBy(() -> service.approve(command))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("INSURANCE_SPECIALIST");
    }

    @Test
    void translatesDomainStateViolationAtApplicationBoundary() {
        service.submit(submitCommand(hospitalActor(PROVIDER_ID)));
        var command = new DecidePreAuthorizationCommand(
                PRE_AUTHORIZATION_ID, "Coverage verified", specialistActor());
        service.approve(command);

        assertThatThrownBy(() -> service.approve(command))
                .isInstanceOf(PreAuthorizationStateConflictException.class)
                .hasMessageContaining("pending");
    }

    private SubmitPreAuthorizationCommand submitCommand(ActorContext actor) {
        return new SubmitPreAuthorizationCommand(
                actor, MEMBER_ID, "POL-100", "J18.9",
                new BigDecimal("1250.00"), Currency.getInstance("TRY"));
    }

    private ActorContext hospitalActor(UUID providerId) {
        return new ActorContext(
                "hospital-user", providerId, Set.of(ApplicationRole.HOSPITAL_USER));
    }

    private ActorContext specialistActor() {
        return new ActorContext(
                "specialist-user", null, Set.of(ApplicationRole.INSURANCE_SPECIALIST));
    }

    private static final class InMemoryPreAuthorizationRepository
            implements PreAuthorizationRepository {
        private final Map<UUID, PreAuthorization> entries = new HashMap<>();

        @Override
        public PreAuthorization save(PreAuthorization preAuthorization) {
            entries.put(preAuthorization.id(), preAuthorization);
            return preAuthorization;
        }

        @Override
        public Optional<PreAuthorization> findById(UUID id) {
            return Optional.ofNullable(entries.get(id));
        }
    }
}
