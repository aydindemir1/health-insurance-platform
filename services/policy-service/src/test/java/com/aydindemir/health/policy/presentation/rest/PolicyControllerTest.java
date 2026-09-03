package com.aydindemir.health.policy.presentation.rest;

import com.aydindemir.health.policy.application.dto.CoverageEvaluationResult;
import com.aydindemir.health.policy.application.dto.CoverageResult;
import com.aydindemir.health.policy.application.dto.PolicyResult;
import com.aydindemir.health.policy.application.port.in.CreatePolicyUseCase;
import com.aydindemir.health.policy.application.port.in.EvaluateCoverageUseCase;
import com.aydindemir.health.policy.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PolicyController.class)
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class PolicyControllerTest {
    private static final UUID POLICY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePolicyUseCase createPolicy;

    @MockitoBean
    private EvaluateCoverageUseCase evaluateCoverage;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createsPolicyForInsuranceSpecialist() throws Exception {
        when(createPolicy.create(any())).thenReturn(policyResult());

        mockMvc.perform(post("/api/v1/policies")
                        .with(jwt().jwt(token -> token.subject("specialist"))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_INSURANCE_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyNumber": "POL-100",
                                  "memberId": "%s",
                                  "validFrom": "2026-01-01",
                                  "validUntil": "2026-12-31",
                                  "coverages": [{
                                    "serviceCode": "IMG-MRI",
                                    "limit": 10000.00,
                                    "currency": "TRY"
                                  }]
                                }
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "http://localhost/api/v1/policies/" + POLICY_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.coverages[0].remaining").value(10000.00));
    }

    @Test
    void returnsCoverageDenialAsAValidBusinessResponse() throws Exception {
        when(evaluateCoverage.evaluate(any())).thenReturn(new CoverageEvaluationResult(
                false, "LIMIT_EXCEEDED", "Requested amount exceeds the remaining coverage limit",
                POLICY_ID, new BigDecimal("500.00"), "TRY"));

        mockMvc.perform(post("/api/v1/coverage-evaluations")
                        .with(jwt().jwt(token -> token.subject("hospital"))
                                .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyNumber": "POL-100",
                                  "memberId": "%s",
                                  "serviceCode": "IMG-MRI",
                                  "requestedAmount": 1000.00,
                                  "currency": "TRY",
                                  "serviceDate": "2026-09-03"
                                }
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.remainingAmount").value(500.00));
    }

    @Test
    void preventsHospitalUserFromCreatingPolicy() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyNumber": "POL-100",
                                  "memberId": "%s",
                                  "validFrom": "2026-01-01",
                                  "validUntil": "2026-12-31",
                                  "coverages": [{
                                    "serviceCode": "IMG-MRI",
                                    "limit": 10000.00,
                                    "currency": "TRY"
                                  }]
                                }
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isForbidden());

        verify(createPolicy, never()).create(any());
    }

    private PolicyResult policyResult() {
        return new PolicyResult(
                POLICY_ID, "POL-100", MEMBER_ID,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                "ACTIVE", List.of(new CoverageResult(
                        "IMG-MRI", new BigDecimal("10000.00"), BigDecimal.ZERO,
                        new BigDecimal("10000.00"), "TRY")));
    }
}
