package com.aydindemir.health.claims.application.port.out;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.event.ClaimSearchProjection;

public interface SearchProjectionExportQuery {
    PageResult<ClaimSearchProjection> findPage(int page, int size);
}
