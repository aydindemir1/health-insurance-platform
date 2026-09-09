package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.dto.PageResult;
import com.aydindemir.health.claims.application.event.ClaimSearchProjection;
import com.aydindemir.health.claims.application.port.in.ExportSearchProjectionsUseCase;
import com.aydindemir.health.claims.application.query.ExportSearchProjectionsQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/search-projections/claims")
class SearchProjectionExportController {
    private final ExportSearchProjectionsUseCase exporter;
    private final AuthenticatedActorMapper actors;

    SearchProjectionExportController(ExportSearchProjectionsUseCase exporter, AuthenticatedActorMapper actors) {
        this.exporter = exporter;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    PageResult<ClaimSearchProjection> export(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            JwtAuthenticationToken authentication) {
        return exporter.export(new ExportSearchProjectionsQuery(actors.from(authentication), page, size));
    }
}
