package com.aydindemir.health.claims.application.usecase;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.event.ClaimSearchProjection;
import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.port.in.ExportSearchProjectionsUseCase;
import com.aydindemir.health.claims.application.port.out.SearchProjectionExportQuery;
import com.aydindemir.health.claims.application.query.ExportSearchProjectionsQuery;
import com.aydindemir.health.claims.application.security.ApplicationRole;

import java.util.Objects;

public final class SearchProjectionExportService implements ExportSearchProjectionsUseCase {
    private final SearchProjectionExportQuery projections;

    public SearchProjectionExportService(SearchProjectionExportQuery projections) {
        this.projections = Objects.requireNonNull(projections);
    }

    @Override
    public PageResult<ClaimSearchProjection> export(ExportSearchProjectionsQuery query) {
        Objects.requireNonNull(query);
        if (!query.actor().hasRole(ApplicationRole.SYSTEM_ADMIN)) {
            throw new ApplicationAccessDeniedException("Required role: SYSTEM_ADMIN");
        }
        return projections.findPage(query.page(), query.size());
    }
}
