package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.dto.AuditRecordResult;
import com.aydindemir.health.policy.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.policy.application.query.SearchAuditRecordsQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies/audit-records")
class PolicyAuditController {
    private final SearchAuditRecordsUseCase search;
    private final AuthenticatedActorMapper actorMapper;

    PolicyAuditController(SearchAuditRecordsUseCase search, AuthenticatedActorMapper actorMapper) {
        this.search = search;
        this.actorMapper = actorMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    PageResponse search(
            @RequestParam(required = false) UUID aggregateId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken authentication) {
        var result = search.search(SearchAuditRecordsQuery.fromRequest(
                actorMapper.from(authentication), aggregateId, action, page, size));
        return PageResponse.from(result);
    }

    record RecordResponse(
            UUID auditId, String aggregateType, UUID aggregateId, String action,
            String actorSubject, Set<String> actorRoles, String correlationId,
            Instant occurredAt, String reasonCode, StatusChange changes,
            String retentionClass) {
        static RecordResponse from(AuditRecordResult source) {
            return new RecordResponse(
                    source.auditId(), source.aggregateType(), source.aggregateId(), source.action(),
                    source.actorSubject(), source.actorRoles(), source.correlationId(), source.occurredAt(),
                    source.reasonCode(), new StatusChange(source.fromStatus(), source.toStatus()),
                    source.retentionClass());
        }
    }

    record StatusChange(String fromStatus, String toStatus) { }

    record PageResponse(
            List<RecordResponse> content, int page, int size, long totalElements,
            int totalPages, boolean first, boolean last) {
        static PageResponse from(
                com.aydindemir.health.policy.application.dto.PageResult<AuditRecordResult> source) {
            return new PageResponse(
                    source.content().stream().map(RecordResponse::from).toList(),
                    source.page(), source.size(), source.totalElements(), source.totalPages(),
                    source.page() == 0,
                    source.totalPages() == 0 || source.page() >= source.totalPages() - 1);
        }
    }
}
