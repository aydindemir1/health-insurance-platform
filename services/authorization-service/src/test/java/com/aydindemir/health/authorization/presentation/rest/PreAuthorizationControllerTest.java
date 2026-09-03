package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.command.SubmitPreAuthorizationCommand;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;
import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.port.in.DecidePreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.GetPreAuthorizationUseCase;
import com.aydindemir.health.authorization.application.port.in.SubmitPreAuthorizationUseCase;
import com.aydindemir.health.authorization.infrastructure.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PreAuthorizationController.class)
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class PreAuthorizationControllerTest {
    private static final UUID PRE_AUTHORIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED_PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID UNTRUSTED_PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitPreAuthorizationUseCase submitUseCase;

    @MockitoBean
    private GetPreAuthorizationUseCase getUseCase;

    @MockitoBean
    private DecidePreAuthorizationUseCase decideUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/pre-authorizations/{id}", PRE_AUTHORIZATION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void derivesProviderFromJwtAndIgnoresClientSuppliedProviderId() throws Exception {
        when(submitUseCase.submit(any())).thenReturn(pendingResult());

        mockMvc.perform(post("/api/v1/pre-authorizations")
                        .with(hospitalJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": "%s",
                                  "providerId": "%s",
                                  "policyNumber": "POL-100",
                                  "diagnosisCode": "J18.9",
                                  "requestedAmount": 1250.00,
                                  "currency": "TRY"
                                }
                                """.formatted(MEMBER_ID, UNTRUSTED_PROVIDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "http://localhost/api/v1/pre-authorizations/" + PRE_AUTHORIZATION_ID))
                .andExpect(jsonPath("$.providerId").value(TRUSTED_PROVIDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        var command = ArgumentCaptor.forClass(SubmitPreAuthorizationCommand.class);
        verify(submitUseCase).submit(command.capture());
        assertThat(command.getValue().actor().providerId()).isEqualTo(TRUSTED_PROVIDER_ID);
    }

    @Test
    void rejectsInvalidRequestBeforeCallingUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/pre-authorizations")
                        .with(hospitalJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": "%s",
                                  "policyNumber": "",
                                  "diagnosisCode": "J18.9",
                                  "requestedAmount": 0,
                                  "currency": "TRY"
                                }
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.policyNumber").exists())
                .andExpect(jsonPath("$.errors.requestedAmount").exists());

        verify(submitUseCase, never()).submit(any());
    }

    @Test
    void preventsHospitalUserFromApproving() throws Exception {
        mockMvc.perform(post("/api/v1/pre-authorizations/{id}/approval", PRE_AUTHORIZATION_ID)
                        .with(hospitalJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Coverage verified"}
                                """))
                .andExpect(status().isForbidden());

        verify(decideUseCase, never()).approve(any());
    }

    @Test
    void rendersApplicationOwnershipFailureAsProblemDetail() throws Exception {
        when(getUseCase.get(any())).thenThrow(new ApplicationAccessDeniedException(
                "Hospital users can only view their own provider's pre-authorizations"));

        mockMvc.perform(get("/api/v1/pre-authorizations/{id}", PRE_AUTHORIZATION_ID)
                        .with(hospitalJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Operation not permitted"))
                .andExpect(jsonPath("$.detail").value(
                        "Hospital users can only view their own provider's pre-authorizations"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor hospitalJwt() {
        return jwt()
                .jwt(token -> token
                        .subject("hospital-user")
                        .claim("provider_id", TRUSTED_PROVIDER_ID.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_USER"));
    }

    private PreAuthorizationResult pendingResult() {
        return new PreAuthorizationResult(
                PRE_AUTHORIZATION_ID,
                MEMBER_ID,
                TRUSTED_PROVIDER_ID,
                "POL-100",
                "J18.9",
                new BigDecimal("1250.00"),
                "TRY",
                "PENDING",
                null,
                Instant.parse("2026-09-03T12:00:00Z"),
                null);
    }
}
