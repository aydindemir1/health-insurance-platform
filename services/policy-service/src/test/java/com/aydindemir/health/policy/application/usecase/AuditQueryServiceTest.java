package com.aydindemir.health.policy.application.usecase;

import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.policy.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.policy.application.security.ActorContext;
import com.aydindemir.health.policy.application.security.ApplicationRole;
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
                new ActorContext("specialist", Set.of(ApplicationRole.INSURANCE_SPECIALIST)),
                null, null, 0, 20)))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    @Test
    void allowsAdministratorWithBoundedPage() {
        var service = new AuditQueryService(criteria ->
                new PageResult<>(List.of(), criteria.page(), criteria.size(), 0, 0));
        service.search(SearchAuditRecordsQuery.fromRequest(
                new ActorContext("admin", Set.of(ApplicationRole.SYSTEM_ADMIN)),
                null, "policy_issued", 0, 100));

        assertThatThrownBy(() -> SearchAuditRecordsQuery.fromRequest(
                new ActorContext("admin", Set.of(ApplicationRole.SYSTEM_ADMIN)),
                null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
