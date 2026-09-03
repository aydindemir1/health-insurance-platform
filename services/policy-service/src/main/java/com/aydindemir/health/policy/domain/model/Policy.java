package com.aydindemir.health.policy.domain.model;

import com.aydindemir.health.policy.domain.valueobject.Money;
import com.aydindemir.health.policy.domain.valueobject.ServiceCode;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Policy {
    private final UUID id;
    private final String policyNumber;
    private final UUID memberId;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final Map<ServiceCode, Coverage> coverages;
    private PolicyStatus status;

    private Policy(
            UUID id,
            String policyNumber,
            UUID memberId,
            LocalDate validFrom,
            LocalDate validUntil,
            PolicyStatus status,
            Collection<Coverage> coverages) {
        this.id = Objects.requireNonNull(id);
        this.policyNumber = requireText(policyNumber);
        this.memberId = Objects.requireNonNull(memberId);
        this.validFrom = Objects.requireNonNull(validFrom);
        this.validUntil = Objects.requireNonNull(validUntil);
        if (validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("Policy end date cannot precede its start date");
        }
        this.status = Objects.requireNonNull(status);
        this.coverages = indexCoverages(coverages);
        if (this.coverages.isEmpty()) {
            throw new IllegalArgumentException("A policy must define at least one coverage");
        }
    }

    public static Policy issue(
            UUID id,
            String policyNumber,
            UUID memberId,
            LocalDate validFrom,
            LocalDate validUntil,
            Collection<Coverage> coverages) {
        return new Policy(id, policyNumber, memberId, validFrom, validUntil,
                PolicyStatus.ACTIVE, coverages);
    }

    public static Policy rehydrate(
            UUID id,
            String policyNumber,
            UUID memberId,
            LocalDate validFrom,
            LocalDate validUntil,
            PolicyStatus status,
            Collection<Coverage> coverages) {
        return new Policy(id, policyNumber, memberId, validFrom, validUntil,
                status, coverages);
    }

    public CoverageDecision evaluate(
            UUID requestedMemberId,
            ServiceCode serviceCode,
            Money requestedAmount,
            LocalDate serviceDate) {
        Objects.requireNonNull(requestedMemberId);
        Objects.requireNonNull(serviceCode);
        Objects.requireNonNull(requestedAmount);
        Objects.requireNonNull(serviceDate);
        if (requestedAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Requested amount must be positive");
        }
        if (!memberId.equals(requestedMemberId)) {
            return CoverageDecision.denied(
                    "MEMBER_MISMATCH", "Policy does not belong to the requested member", null);
        }
        if (status != PolicyStatus.ACTIVE) {
            return CoverageDecision.denied(
                    "POLICY_INACTIVE", "Policy is not active", null);
        }
        if (serviceDate.isBefore(validFrom)) {
            return CoverageDecision.denied(
                    "POLICY_NOT_YET_EFFECTIVE", "Policy is not effective on the service date", null);
        }
        if (serviceDate.isAfter(validUntil)) {
            return CoverageDecision.denied(
                    "POLICY_EXPIRED", "Policy has expired", null);
        }
        Coverage coverage = coverages.get(serviceCode);
        if (coverage == null) {
            return CoverageDecision.denied(
                    "SERVICE_NOT_COVERED", "Requested service is not covered", null);
        }
        if (!coverage.limit().currency().equals(requestedAmount.currency())) {
            return CoverageDecision.denied(
                    "CURRENCY_MISMATCH", "Requested currency does not match the coverage", coverage.remaining());
        }
        if (requestedAmount.isGreaterThan(coverage.remaining())) {
            return CoverageDecision.denied(
                    "LIMIT_EXCEEDED", "Requested amount exceeds the remaining coverage limit", coverage.remaining());
        }
        return CoverageDecision.eligible(coverage.remaining());
    }

    public void suspend() {
        if (status != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("Only an active policy can be suspended");
        }
        status = PolicyStatus.SUSPENDED;
    }

    private static Map<ServiceCode, Coverage> indexCoverages(Collection<Coverage> source) {
        Objects.requireNonNull(source);
        var indexed = new LinkedHashMap<ServiceCode, Coverage>();
        for (Coverage coverage : source) {
            if (indexed.put(coverage.serviceCode(), coverage) != null) {
                throw new IllegalArgumentException(
                        "Duplicate coverage service code: " + coverage.serviceCode().value());
            }
        }
        return indexed;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Policy number must not be blank");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public String policyNumber() { return policyNumber; }
    public UUID memberId() { return memberId; }
    public LocalDate validFrom() { return validFrom; }
    public LocalDate validUntil() { return validUntil; }
    public PolicyStatus status() { return status; }
    public List<Coverage> coverages() { return List.copyOf(coverages.values()); }
}
