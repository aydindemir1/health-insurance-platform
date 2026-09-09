package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.authorization.application.query.SearchAuditRecordsQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-records")
class AuditController {
    private final SearchAuditRecordsUseCase search;
    private final AuthenticatedActorMapper actorMapper;

    AuditController(SearchAuditRecordsUseCase search, AuthenticatedActorMapper actorMapper) {
        this.search = search;
        this.actorMapper = actorMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    AuditRecordResponse.Page search(
            @RequestParam(required = false) UUID aggregateId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken authentication) {
        return AuditRecordResponse.Page.from(search.search(SearchAuditRecordsQuery.fromRequest(
                actorMapper.from(authentication), aggregateId, action, page, size)));
    }
}
