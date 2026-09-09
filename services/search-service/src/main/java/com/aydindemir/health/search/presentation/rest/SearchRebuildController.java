package com.aydindemir.health.search.presentation.rest;

import com.aydindemir.health.search.application.dto.SearchRebuildResult;
import com.aydindemir.health.search.application.dto.SearchRebuildRecord;
import com.aydindemir.health.search.application.port.in.SearchRebuildUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/search-rebuilds")
class SearchRebuildController {
    private final SearchRebuildUseCase rebuilds;
    private final AuthenticatedActorMapper actors;

    SearchRebuildController(SearchRebuildUseCase rebuilds, AuthenticatedActorMapper actors) {
        this.rebuilds = rebuilds;
        this.actors = actors;
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    SearchRebuildResult create(@RequestBody CreateRequest request, JwtAuthenticationToken authentication) {
        return rebuilds.create(actors.from(authentication), request.schemaVersion());
    }

    @PostMapping("/{runId}/records")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    SearchRebuildResult ingest(
            @PathVariable UUID runId, @RequestBody BatchRequest request,
            JwtAuthenticationToken authentication) {
        return rebuilds.ingest(actors.from(authentication), runId, request.records());
    }

    @PostMapping("/{runId}/activation")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    SearchRebuildResult activate(
            @PathVariable UUID runId, @RequestBody ActivationRequest request,
            JwtAuthenticationToken authentication) {
        return rebuilds.activate(actors.from(authentication), runId, request.expectedDocumentCount());
    }

    @PostMapping("/{runId}/rollback")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    SearchRebuildResult rollback(@PathVariable UUID runId, JwtAuthenticationToken authentication) {
        return rebuilds.rollback(actors.from(authentication), runId);
    }

    record CreateRequest(int schemaVersion) { }
    record BatchRequest(List<SearchRebuildRecord> records) { }
    record ActivationRequest(long expectedDocumentCount) { }
}
