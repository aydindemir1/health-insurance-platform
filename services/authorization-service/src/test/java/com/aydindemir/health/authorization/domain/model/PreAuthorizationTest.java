package com.aydindemir.health.authorization.domain.model;

import com.aydindemir.health.authorization.domain.exception.InvalidPreAuthorizationStateException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void submitsRequestAsPending() {
        var request = newRequest();

        assertThat(request.status()).isEqualTo(PreAuthorizationStatus.PENDING);
        assertThat(request.serviceCode()).isEqualTo("IMG-MRI");
        assertThat(request.createdAt()).isEqualTo(NOW);
        assertThat(request.decidedAt()).isNull();
    }

    @Test
    void approvesPendingRequest() {
        var request = newRequest();

        request.approve("Coverage verified", CLOCK);

        assertThat(request.status()).isEqualTo(PreAuthorizationStatus.APPROVED);
        assertThat(request.decisionReason()).isEqualTo("Coverage verified");
        assertThat(request.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsPendingRequestWithMandatoryReason() {
        var request = newRequest();

        request.reject("Policy limit exceeded", CLOCK);

        assertThat(request.status()).isEqualTo(PreAuthorizationStatus.REJECTED);
        assertThat(request.decisionReason()).isEqualTo("Policy limit exceeded");
    }

    @Test
    void preventsSecondDecision() {
        var request = newRequest();
        request.approve("Coverage verified", CLOCK);

        assertThatThrownBy(() -> request.reject("Changed mind", CLOCK))
                .isInstanceOf(InvalidPreAuthorizationStateException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> PreAuthorization.submit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POL-100",
                "IMG-MRI", "J18.9", BigDecimal.ZERO, Currency.getInstance("TRY"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private PreAuthorization newRequest() {
        return PreAuthorization.submit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POL-100",
                "img-mri", "J18.9", new BigDecimal("1250.00"),
                Currency.getInstance("TRY"), CLOCK);
    }
}
