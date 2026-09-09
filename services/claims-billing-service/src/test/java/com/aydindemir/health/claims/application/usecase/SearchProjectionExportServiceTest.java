package com.aydindemir.health.claims.application.usecase;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.query.ExportSearchProjectionsQuery;
import com.aydindemir.health.claims.application.security.ActorContext;
import com.aydindemir.health.claims.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchProjectionExportServiceTest {
    @Test
    void allowsOnlySystemAdministratorsAndCapsPageSize() {
        var called = new AtomicBoolean();
        var service = new SearchProjectionExportService((page, size) -> {
            called.set(true);
            return new PageResult<>(List.of(), page, size, 0, 0);
        });

        service.export(new ExportSearchProjectionsQuery(actor(ApplicationRole.SYSTEM_ADMIN), 0, 200));
        assertThat(called).isTrue();
        assertThatThrownBy(() -> new ExportSearchProjectionsQuery(
                actor(ApplicationRole.SYSTEM_ADMIN), 0, 201))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deniesOtherRolesBeforeStorageAccess() {
        var service = new SearchProjectionExportService((page, size) -> {
            throw new AssertionError("storage must not be queried");
        });

        assertThatThrownBy(() -> service.export(new ExportSearchProjectionsQuery(
                actor(ApplicationRole.CLAIM_APPROVER), 0, 100)))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    private ActorContext actor(ApplicationRole role) {
        return new ActorContext("subject", null, Set.of(role));
    }
}
