package com.aydindemir.health.authorization.application.usecase;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.port.out.PreAuthorizationRepository;
import com.aydindemir.health.authorization.application.query.ExportSearchProjectionsQuery;
import com.aydindemir.health.authorization.application.security.ActorContext;
import com.aydindemir.health.authorization.application.security.ApplicationRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchProjectionExportServiceTest {
    @Test
    void allowsOnlySystemAdministratorsAndKeepsThePageBounded() {
        PreAuthorizationRepository repository = mock(PreAuthorizationRepository.class);
        when(repository.search(any())).thenReturn(new PageResult<>(List.of(), 0, 200, 0, 0));
        var service = new SearchProjectionExportService(repository);

        service.export(new ExportSearchProjectionsQuery(actor(ApplicationRole.SYSTEM_ADMIN), 0, 200));

        assertThatThrownBy(() -> new ExportSearchProjectionsQuery(
                actor(ApplicationRole.SYSTEM_ADMIN), 0, 201))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deniesOtherRolesBeforeStorageAccess() {
        PreAuthorizationRepository repository = mock(PreAuthorizationRepository.class);
        var service = new SearchProjectionExportService(repository);

        assertThatThrownBy(() -> service.export(new ExportSearchProjectionsQuery(
                actor(ApplicationRole.INSURANCE_SPECIALIST), 0, 100)))
                .isInstanceOf(ApplicationAccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    private ActorContext actor(ApplicationRole role) {
        return new ActorContext("subject", null, Set.of(role));
    }
}
