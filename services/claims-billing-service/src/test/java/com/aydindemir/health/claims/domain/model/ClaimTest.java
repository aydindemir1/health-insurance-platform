package com.aydindemir.health.claims.domain.model;

import com.aydindemir.health.claims.domain.exception.InvalidClaimStateException;
import com.aydindemir.health.claims.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    void submitsClaimFromApprovedPreAuthorizationSnapshot() {
        Claim claim = claim();

        assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.preAuthorizationId()).isNotNull();
        assertThat(claim.serviceCode()).isEqualTo("IMG-MRI");
        assertThat(claim.submittedAt()).isEqualTo(NOW);
    }

    @Test
    void followsReviewAndApprovalStateTransitions() {
        Claim claim = claim();

        claim.startReview(CLOCK);
        claim.approve(money("900.00"), CLOCK);

        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.approvedAmount().amount()).isEqualByComparingTo("900.00");
        assertThat(claim.decidedAt()).isEqualTo(NOW);
    }

    @Test
    void preventsApprovalBeforeReview() {
        assertThatThrownBy(() -> claim().approve(money("900.00"), CLOCK))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("under review");
    }

    @Test
    void preventsApprovalAboveClaimedAmount() {
        Claim claim = claim();
        claim.startReview(CLOCK);

        assertThatThrownBy(() -> claim.approve(money("1000.01"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed claimed");
    }

    @Test
    void requiresReasonWhenRejectingClaim() {
        Claim claim = claim();
        claim.startReview(CLOCK);

        assertThatThrownBy(() -> claim.reject(" ", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason");
    }

    private Claim claim() {
        return Claim.submit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "POL-100", "img-mri", money("1000.00"), CLOCK);
    }

    private Money money(String value) {
        return new Money(new BigDecimal(value), TRY);
    }
}
