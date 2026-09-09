package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.audit.AuditAction;
import com.aydindemir.health.authorization.application.audit.AuditChanges;
import com.aydindemir.health.authorization.application.audit.AuditReasonCode;
import com.aydindemir.health.authorization.application.dto.AuditRecordResult;
import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.authorization.application.query.SearchAuditRecordsQuery;
import com.aydindemir.health.authorization.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class AuditControllerTest {
    private static final UUID AGGREGATE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @MockitoBean SearchAuditRecordsUseCase search;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesInsuranceSpecialistAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/audit-records")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_INSURANCE_SPECIALIST"))))
                .andExpect(status().isForbidden());

        verify(search, never()).search(any());
    }

    @Test
    void returnsMinimizedPageToSystemAdministrator() throws Exception {
        when(search.search(any())).thenReturn(new PageResult<>(List.of(record()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/audit-records")
                        .param("aggregateId", AGGREGATE_ID.toString())
                        .param("action", "PRE_AUTHORIZATION_APPROVED")
                        .with(jwt().jwt(token -> token.subject("administrator"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].aggregateId").value(AGGREGATE_ID.toString()))
                .andExpect(jsonPath("$.content[0].action").value("PRE_AUTHORIZATION_APPROVED"))
                .andExpect(jsonPath("$.content[0].changes.fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].changes.toStatus").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.content[0].policyNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].diagnosisCode").doesNotExist())
                .andExpect(jsonPath("$.content[0].amount").doesNotExist());

        var query = ArgumentCaptor.forClass(SearchAuditRecordsQuery.class);
        verify(search).search(query.capture());
        assertThat(query.getValue().actor().subject()).isEqualTo("administrator");
        assertThat(query.getValue().aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(query.getValue().action()).isEqualTo(AuditAction.PRE_AUTHORIZATION_APPROVED);
    }

    private AuditRecordResult record() {
        return new AuditRecordResult(
                UUID.randomUUID(), "PRE_AUTHORIZATION", AGGREGATE_ID,
                AuditAction.PRE_AUTHORIZATION_APPROVED, "specialist",
                Set.of("INSURANCE_SPECIALIST"), null, "correlation-id",
                Instant.parse("2026-09-09T12:00:00Z"), AuditReasonCode.SPECIALIST_DECISION,
                new AuditChanges("PENDING", "APPROVED"), "AUDIT_EVIDENCE");
    }
}
