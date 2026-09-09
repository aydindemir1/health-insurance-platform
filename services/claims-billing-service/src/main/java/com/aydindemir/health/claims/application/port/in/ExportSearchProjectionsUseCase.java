package com.aydindemir.health.claims.application.port.in;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.event.ClaimSearchProjection;
import com.aydindemir.health.claims.application.query.ExportSearchProjectionsQuery;

public interface ExportSearchProjectionsUseCase {
    PageResult<ClaimSearchProjection> export(ExportSearchProjectionsQuery query);
}
