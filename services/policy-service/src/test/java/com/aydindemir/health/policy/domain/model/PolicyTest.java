package com.aydindemir.health.policy.domain.model;

import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTest {
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Currency TRY = Currency.getInstance("TRY");
    private static final ServiceCode MRI = new ServiceCode("IMG-MRI");

    @Test
    void acceptsCoveredServiceWithinValidityAndRemainingLimit() {
        var decision = policy().evaluate(
                MEMBER_ID, MRI, money("2500.00"), LocalDate.parse("2026-09-03"));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.code()).isEqualTo("ELIGIBLE");
        assertThat(decision.remaining().amount()).isEqualByComparingTo("8000.00");
    }

    @Test
    void rejectsExpiredPolicy() {
        var decision = policy().evaluate(
                MEMBER_ID, MRI, money("2500.00"), LocalDate.parse("2027-01-01"));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.code()).isEqualTo("POLICY_EXPIRED");
    }

    @Test
    void rejectsServiceOutsideCoverage() {
        var decision = policy().evaluate(
                MEMBER_ID, new ServiceCode("DENTAL-IMPLANT"),
                money("1000.00"), LocalDate.parse("2026-09-03"));

        assertThat(decision.code()).isEqualTo("SERVICE_NOT_COVERED");
    }

    @Test
    void rejectsAmountAboveRemainingLimit() {
        var decision = policy().evaluate(
                MEMBER_ID, MRI, money("8000.01"), LocalDate.parse("2026-09-03"));

        assertThat(decision.code()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(decision.remaining().amount()).isEqualByComparingTo("8000.00");
    }

    @Test
    void rejectsMemberMismatchBeforeExposingCoverage() {
        var decision = policy().evaluate(
                UUID.randomUUID(), MRI, money("1.00"), LocalDate.parse("2026-09-03"));

        assertThat(decision.code()).isEqualTo("MEMBER_MISMATCH");
        assertThat(decision.remaining()).isNull();
    }

    @Test
    void preventsDuplicateCoverageDefinitions() {
        Coverage coverage = coverage();

        assertThatThrownBy(() -> Policy.issue(
                UUID.randomUUID(), "POL-100", MEMBER_ID,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of(coverage, coverage)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate coverage");
    }

    private Policy policy() {
        return Policy.issue(
                UUID.randomUUID(), "POL-100", MEMBER_ID,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of(coverage()));
    }

    private Coverage coverage() {
        return new Coverage(MRI, money("10000.00"), money("2000.00"));
    }

    private Money money(String amount) {
        return new Money(new BigDecimal(amount), TRY);
    }
}
