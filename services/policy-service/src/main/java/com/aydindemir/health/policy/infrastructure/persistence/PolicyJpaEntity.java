package com.aydindemir.health.policy.infrastructure.persistence;

import com.aydindemir.health.policy.domain.model.PolicyStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policies")
class PolicyJpaEntity {
    @Id
    UUID id;

    @Column(name = "policy_number", nullable = false, length = 50)
    String policyNumber;

    @Column(name = "member_id", nullable = false)
    UUID memberId;

    @Column(name = "valid_from", nullable = false)
    LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    PolicyStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "policy_coverages",
            joinColumns = @JoinColumn(name = "policy_id"))
    List<CoverageJpaEmbeddable> coverages = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    long version;
}
