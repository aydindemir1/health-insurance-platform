package com.aydindemir.health.search.infrastructure.messaging;

import com.aydindemir.health.search.application.port.in.IndexSearchRecordUseCase;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchProjectionListenersTest {
    private SearchRecord indexed;
    private final SearchProjectionListeners listeners = new SearchProjectionListeners(
            JsonMapper.builder().findAndAddModules().build(), record -> indexed = record);

    @Test
    void mapsPreAuthorizationDecisionToIdempotentDocumentId() {
        UUID id = UUID.randomUUID();
        listeners.consumePreAuthorization("""
                {"eventId":"%s","eventType":"PreAuthorizationDecided","eventVersion":1,
                 "preAuthorizationId":"%s","memberId":"%s","providerId":"%s",
                 "policyNumber":"POL-100","serviceCode":"IMG-MRI","requestedAmount":1250.00,
                 "currency":"TRY","decision":"APPROVED","reason":null,
                 "occurredAt":"2026-09-09T00:00:00Z"}
                """.formatted(UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID()));

        assertThat(indexed.id()).isEqualTo("PRE_AUTHORIZATION:" + id);
        assertThat(indexed.type()).isEqualTo(RecordType.PRE_AUTHORIZATION);
        assertThat(indexed.status()).isEqualTo("APPROVED");
        assertThat(indexed.sourceRevision()).isEqualTo(1);
    }

    @Test
    void mapsLatestClaimAndInvoiceState() {
        UUID claimId = UUID.randomUUID();
        var message = new ClaimSearchProjectionMessage(
                UUID.randomUUID(), "ClaimSearchProjectionUpdated", 1, claimId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POL-100", "IMG-MRI",
                new BigDecimal("1000.00"), new BigDecimal("800.00"), new BigDecimal("800.00"),
                new BigDecimal("800.00"), "TRY", "APPROVED", "SETTLED", "INV-100",
                4L,
                Instant.parse("2026-09-09T00:00:00Z"));
        var mapper = JsonMapper.builder().findAndAddModules().build();

        listeners.consumeClaim(mapper.writeValueAsString(message));

        assertThat(indexed.id()).isEqualTo("CLAIM:" + claimId);
        assertThat(indexed.invoiceStatus()).isEqualTo("SETTLED");
        assertThat(indexed.paidAmount()).isEqualByComparingTo("800.00");
        assertThat(indexed.sourceRevision()).isEqualTo(4);
    }
}
