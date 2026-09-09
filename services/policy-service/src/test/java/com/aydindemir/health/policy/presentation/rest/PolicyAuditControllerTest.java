package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.dto.AuditRecordResult;
import com.aydindemir.health.policy.application.dto.PageResult;
import com.aydindemir.health.policy.application.port.in.SearchAuditRecordsUseCase;
import com.aydindemir.health.policy.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PolicyAuditController.class)
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class PolicyAuditControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SearchAuditRecordsUseCase search;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void deniesNonAdministrator() throws Exception {
        mockMvc.perform(get("/api/v1/policies/audit-records")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_INSURANCE_SPECIALIST"))))
                .andExpect(status().isForbidden());
        verify(search, never()).search(any());
    }

    @Test
    void returnsMinimizedAuditToAdministrator() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        when(search.search(any())).thenReturn(new PageResult<>(List.of(
                new AuditRecordResult(
                        UUID.randomUUID(), "POLICY", aggregateId, "POLICY_ISSUED",
                        "specialist", Set.of("INSURANCE_SPECIALIST"), "correlation-id",
                        Instant.parse("2026-09-09T12:00:00Z"), "USER_CREATION",
                        null, "ACTIVE", "AUDIT_EVIDENCE")), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/policies/audit-records")
                        .with(jwt().jwt(token -> token.subject("admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].aggregateId").value(aggregateId.toString()))
                .andExpect(jsonPath("$.content[0].action").value("POLICY_ISSUED"))
                .andExpect(jsonPath("$.content[0].changes.toStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].policyNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.content[0].coverages").doesNotExist());
    }
}
