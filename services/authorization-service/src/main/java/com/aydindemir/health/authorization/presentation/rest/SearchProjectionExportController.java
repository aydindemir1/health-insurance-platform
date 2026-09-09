package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.dto.SearchProjectionExportRecord;
import com.aydindemir.health.authorization.application.port.in.ExportSearchProjectionsUseCase;
import com.aydindemir.health.authorization.application.query.ExportSearchProjectionsQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/search-projections/pre-authorizations")
class SearchProjectionExportController {
    private final ExportSearchProjectionsUseCase exporter;
    private final AuthenticatedActorMapper actors;

    SearchProjectionExportController(ExportSearchProjectionsUseCase exporter, AuthenticatedActorMapper actors) {
        this.exporter = exporter;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    PageResult<SearchProjectionExportRecord> export(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            JwtAuthenticationToken authentication) {
        return exporter.export(new ExportSearchProjectionsQuery(actors.from(authentication), page, size));
    }
}
