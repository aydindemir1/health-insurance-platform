package com.aydindemir.health.authorization.application.port.in;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.dto.SearchProjectionExportRecord;
import com.aydindemir.health.authorization.application.query.ExportSearchProjectionsQuery;

public interface ExportSearchProjectionsUseCase {
    PageResult<SearchProjectionExportRecord> export(ExportSearchProjectionsQuery query);
}
