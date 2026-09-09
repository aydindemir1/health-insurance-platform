package com.aydindemir.health.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
    @Autowired ElasticsearchClient client;

    @Test
    void indexesAndFiltersHealthcareRecords() {
        UUID providerId = UUID.randomUUID();
        index.save(record(providerId));

        await().untilAsserted(() -> {
            assertThat(client.indices().existsAlias(request -> request.name("healthcare-operations")).value())
                    .isTrue();
            var page = index.search("POL-SEARCH", RecordType.CLAIM, "APPROVED", providerId, 0, 10);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content()).singleElement().satisfies(record ->
                    assertThat(record.invoiceStatus()).isEqualTo("RECONCILED"));
        });
    }

    @Test
    void attachesTheStableAliasToAnExistingLegacyIndexWithoutDeletingIt() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String legacyIndex = "healthcare-operations-v1-" + suffix;
        String alias = "healthcare-operations-" + suffix;
        client.indices().create(request -> request.index(legacyIndex));

        var migratingIndex = new ElasticsearchSearchIndex(client, alias, legacyIndex);
        migratingIndex.save(record(UUID.randomUUID()));

        assertThat(client.indices().exists(request -> request.index(legacyIndex)).value()).isTrue();
        assertThat(client.indices().existsAlias(request -> request.name(alias)).value()).isTrue();
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
