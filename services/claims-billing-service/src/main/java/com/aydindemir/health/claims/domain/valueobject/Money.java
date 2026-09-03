package com.aydindemir.health.claims.domain.valueobject;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
        amount = amount.stripTrailingZeros();
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money positive(BigDecimal amount, Currency currency) {
        Money money = new Money(amount, currency);
        if (money.amount.signum() == 0) {
            throw new IllegalArgumentException("Money amount must be positive");
        }
        return money;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isEqualTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) == 0;
    }

    public void requireSameCurrency(Money other) {
        Objects.requireNonNull(other);
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currencies must match");
        }
    }
}
