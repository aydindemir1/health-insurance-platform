package com.aydindemir.health.claims.application.usecase;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditQueryServiceTest {
    @Test
    void deniesNonAdministratorBeforeStorageAccess() {
        var service = new AuditQueryService(criteria -> {
            throw new AssertionError("storage must not be queried");
        });

        assertThatThrownBy(() -> service.search(SearchAuditRecordsQuery.fromRequest(
                new ActorContext("approver", null, Set.of(ApplicationRole.CLAIM_APPROVER)),
                null, null, 0, 20)))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    @Test
    void allowsAdministratorWithControlledActionAndBoundedPage() {
        var service = new AuditQueryService(criteria ->
                new PageResult<>(List.of(), criteria.page(), criteria.size(), 0, 0));
        service.search(SearchAuditRecordsQuery.fromRequest(
                new ActorContext("admin", null, Set.of(ApplicationRole.SYSTEM_ADMIN)),
                null, "payment_recorded", 0, 100));

        assertThatThrownBy(() -> SearchAuditRecordsQuery.fromRequest(
                new ActorContext("admin", null, Set.of(ApplicationRole.SYSTEM_ADMIN)),
                null, "unknown_action", 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchAuditRecordsQuery.fromRequest(
                new ActorContext("admin", null, Set.of(ApplicationRole.SYSTEM_ADMIN)),
                null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
