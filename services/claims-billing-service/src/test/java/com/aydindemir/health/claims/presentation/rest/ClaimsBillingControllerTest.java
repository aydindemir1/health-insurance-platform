package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.command.CreateClaimCommand;
import com.aydindemir.health.claims.application.dto.ClaimInvoiceResult;
import com.aydindemir.health.claims.application.dto.ClaimResult;
import com.aydindemir.health.claims.application.dto.InvoiceResult;
import com.aydindemir.health.claims.application.port.in.CreateClaimUseCase;
import com.aydindemir.health.claims.application.port.in.ManageInvoiceUseCase;
import com.aydindemir.health.claims.application.port.in.GetClaimsBillingUseCase;
import com.aydindemir.health.claims.application.port.in.ReviewClaimUseCase;
import com.aydindemir.health.claims.infrastructure.security.SecurityConfiguration;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClaimsBillingController.class)
@Import({SecurityConfiguration.class, AuthenticatedActorMapper.class})
class ClaimsBillingControllerTest {
    private static final UUID CLAIM_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PRE_AUTH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROVIDER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID INVOICE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean CreateClaimUseCase createClaim;
    @MockitoBean ReviewClaimUseCase reviewClaim;
    @MockitoBean ManageInvoiceUseCase manageInvoice;
    @MockitoBean GetClaimsBillingUseCase getClaimsBilling;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void derivesProviderOwnershipFromJwtWhenCreatingClaim() throws Exception {
        when(createClaim.create(any())).thenReturn(result());
        mvc.perform(post("/api/v1/claims").with(hospitalJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preAuthorizationId":"%s","invoiceNumber":"INV-100",
                                 "invoicedAmount":900.00,"currency":"TRY"}
                                """.formatted(PRE_AUTH_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/claims/" + CLAIM_ID))
                .andExpect(jsonPath("$.claim.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"));

        var captor = ArgumentCaptor.forClass(CreateClaimCommand.class);
        verify(createClaim).create(captor.capture());
        assertThat(captor.getValue().actor().providerId()).isEqualTo(PROVIDER_ID);
    }

    @Test
    void preventsHospitalRoleFromApprovingClaims() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/approval", CLAIM_ID).with(hospitalJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":800.00,\"currency\":\"TRY\"}"))
                .andExpect(status().isForbidden());
        verify(reviewClaim, never()).approve(any());
    }

    @Test
    void validatesPaymentBeforeCallingUseCase() throws Exception {
        mvc.perform(post("/api/v1/invoices/{id}/payments", INVOICE_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INSURANCE_SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"\",\"amount\":0,\"currency\":\"TRY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"));
        verify(manageInvoice, never()).recordPayment(any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor hospitalJwt() {
        return jwt().jwt(token -> token.claim("provider_id", PROVIDER_ID.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_USER"));
    }

    private ClaimInvoiceResult result() {
        var claim = new ClaimResult(CLAIM_ID, PRE_AUTH_ID, MEMBER_ID, PROVIDER_ID, "POL-100",
                "IMG-MRI", new BigDecimal("900.00"), null, "TRY", "SUBMITTED", null,
                NOW, null, null);
        var invoice = new InvoiceResult(INVOICE_ID, CLAIM_ID, PROVIDER_ID, "INV-100",
                new BigDecimal("900.00"), null, BigDecimal.ZERO, "TRY", "ISSUED",
                List.of(), NOW, null, null);
        return new ClaimInvoiceResult(claim, invoice);
    }
}
