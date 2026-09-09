package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.audit.AuditAction;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditQueryServiceTest {
    @Test
    void allowsSystemAdministratorAndForwardsBoundedCriteria() {
        var captured = new AtomicReference<com.aydindemir.health.authorization.application.query.AuditRecordSearchCriteria>();
        var service = new AuditQueryService(criteria -> {
            captured.set(criteria);
            return new PageResult<>(List.of(), criteria.page(), criteria.size(), 0, 0);
        });
        UUID aggregateId = UUID.randomUUID();

        var result = service.search(SearchAuditRecordsQuery.fromRequest(
                actor(ApplicationRole.SYSTEM_ADMIN), aggregateId,
                "pre_authorization_approved", 1, 25));

        assertThat(result.content()).isEmpty();
        assertThat(captured.get().aggregateId()).isEqualTo(aggregateId);
        assertThat(captured.get().action()).isEqualTo(AuditAction.PRE_AUTHORIZATION_APPROVED);
        assertThat(captured.get().page()).isEqualTo(1);
        assertThat(captured.get().size()).isEqualTo(25);
    }

    @Test
    void deniesNonAdministratorBeforeQueryingStorage() {
        var service = new AuditQueryService(criteria -> {
            throw new AssertionError("storage must not be queried");
        });

        assertThatThrownBy(() -> service.search(SearchAuditRecordsQuery.fromRequest(
                actor(ApplicationRole.INSURANCE_SPECIALIST), null, null, 0, 20)))
                .isInstanceOf(ApplicationAccessDeniedException.class)
                .hasMessageContaining("system administrators");
    }

    @Test
    void rejectsUnboundedPageSizeAndUnknownAction() {
        assertThatThrownBy(() -> SearchAuditRecordsQuery.fromRequest(
                actor(ApplicationRole.SYSTEM_ADMIN), null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");
        assertThatThrownBy(() -> SearchAuditRecordsQuery.fromRequest(
                actor(ApplicationRole.SYSTEM_ADMIN), null, "UNKNOWN", 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported audit action");
    }

    private ActorContext actor(ApplicationRole role) {
        return new ActorContext("subject", null, Set.of(role));
    }
}
