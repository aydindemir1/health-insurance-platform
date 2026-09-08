package com.aydindemir.health.search.infrastructure.elasticsearch;

import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.messaging.consumer.enabled=false")
class ElasticsearchSearchIndexIntegrationTest {
    @Container
    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.5.3")
            .withEnv("xpack.security.enabled", "false");

    @Autowired SearchIndex index;

    @Test
    void indexesAndFiltersHealthcareRecords() {
        UUID providerId = UUID.randomUUID();
        index.save(record(providerId));

        await().untilAsserted(() -> {
            var page = index.search("POL-SEARCH", RecordType.CLAIM, "APPROVED", providerId, 0, 10);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content()).singleElement().satisfies(record ->
                    assertThat(record.invoiceStatus()).isEqualTo("RECONCILED"));
        });
    }

    private SearchRecord record(UUID providerId) {
        UUID claimId = UUID.randomUUID();
        return new SearchRecord(
                "CLAIM:" + claimId, RecordType.CLAIM, claimId, UUID.randomUUID(), UUID.randomUUID(),
                providerId, "POL-SEARCH", "IMG-MRI", "APPROVED", "RECONCILED", "INV-SEARCH",
                new BigDecimal("1000.00"), new BigDecimal("800.00"), BigDecimal.ZERO,
                "TRY", null, Instant.parse("2026-09-09T00:00:00Z"));
    }
}
