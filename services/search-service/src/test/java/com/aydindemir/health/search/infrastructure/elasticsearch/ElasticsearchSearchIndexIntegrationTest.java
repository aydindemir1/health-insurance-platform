package com.aydindemir.health.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.application.port.out.SearchRebuildIndex;
import com.aydindemir.health.search.application.dto.SearchRebuildRecord;
import com.aydindemir.health.search.application.security.ActorContext;
import com.aydindemir.health.search.application.security.ApplicationRole;
import com.aydindemir.health.search.application.usecase.SearchRebuildService;
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
import java.util.Set;
import java.util.Map;

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
    @Autowired SearchRebuildIndex rebuildIndex;

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

    @Test
    void ignoresAnOlderProjectionRevisionAfterANewerStateWasIndexed() {
        UUID providerId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        index.save(record(providerId, claimId, "SETTLED", 4));
        index.save(record(providerId, claimId, "ISSUED", 2));

        await().untilAsserted(() -> {
            var page = index.search("POL-SEARCH", RecordType.CLAIM, "APPROVED", providerId, 0, 10);
            assertThat(page.content()).filteredOn(record -> record.sourceId().equals(claimId))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record.invoiceStatus()).isEqualTo("SETTLED");
                        assertThat(record.sourceRevision()).isEqualTo(4);
                    });
        });
    }

    @Test
    void readsLegacyDocumentsWithoutASourceRevisionAtTheBaselineRevision() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        client.index(request -> request
                .index("healthcare-operations")
                .id("CLAIM:" + sourceId)
                .document(Map.ofEntries(
                        Map.entry("id", "CLAIM:" + sourceId),
                        Map.entry("type", "CLAIM"),
                        Map.entry("sourceId", sourceId.toString()),
                        Map.entry("preAuthorizationId", UUID.randomUUID().toString()),
                        Map.entry("memberId", UUID.randomUUID().toString()),
                        Map.entry("providerId", providerId.toString()),
                        Map.entry("policyNumber", "POL-LEGACY"),
                        Map.entry("serviceCode", "IMG-MRI"),
                        Map.entry("status", "APPROVED"),
                        Map.entry("amount", 1000),
                        Map.entry("currency", "TRY"),
                        Map.entry("occurredAt", "2026-09-09T00:00:00Z"))));

        await().untilAsserted(() -> {
            var page = index.search("POL-LEGACY", RecordType.CLAIM, "APPROVED", providerId, 0, 10);
            assertThat(page.content()).singleElement()
                    .extracting(SearchRecord::sourceRevision)
                    .isEqualTo(1L);
        });
    }

    @Test
    void activatesAndRollsBackAVersionedCandidateWithAtomicAliasSwaps() throws Exception {
        var rebuilds = new SearchRebuildService(rebuildIndex);
        var admin = new ActorContext("admin", null, Set.of(ApplicationRole.SYSTEM_ADMIN));
        String predecessor = rebuildIndex.currentIndex();
        var created = rebuilds.create(admin, 2);
        SearchRecord candidateRecord = record(UUID.randomUUID());

        rebuilds.ingest(admin, created.runId(), java.util.List.of(toRebuildRecord(candidateRecord)));
        var active = rebuilds.activate(admin, created.runId(), 1);

        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(rebuildIndex.currentIndex()).isEqualTo(created.candidateIndex());
        assertThat(client.indices().exists(request -> request.index(predecessor)).value()).isTrue();

        var rolledBack = rebuilds.rollback(admin, created.runId());
        assertThat(rolledBack.status()).isEqualTo("ROLLED_BACK");
        assertThat(rebuildIndex.currentIndex()).isEqualTo(predecessor);
        assertThat(client.indices().exists(request -> request.index(created.candidateIndex())).value()).isTrue();
    }

    private SearchRecord record(UUID providerId) {
        return record(providerId, UUID.randomUUID(), "RECONCILED", 1);
    }

    private SearchRecord record(UUID providerId, UUID claimId, String invoiceStatus, long sourceRevision) {
        return new SearchRecord(
                "CLAIM:" + claimId, RecordType.CLAIM, claimId, UUID.randomUUID(), UUID.randomUUID(),
                providerId, "POL-SEARCH", "IMG-MRI", "APPROVED", invoiceStatus, "INV-SEARCH",
                new BigDecimal("1000.00"), new BigDecimal("800.00"), BigDecimal.ZERO,
                "TRY", null, sourceRevision, Instant.parse("2026-09-09T00:00:00Z"));
    }

    private SearchRebuildRecord toRebuildRecord(SearchRecord value) {
        return new SearchRebuildRecord(
                value.id(), value.type(), value.sourceId(), value.preAuthorizationId(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status(), value.invoiceStatus(), value.invoiceNumber(), value.amount(),
                value.approvedAmount(), value.paidAmount(), value.currency(), value.reason(),
                value.sourceRevision(), value.occurredAt());
    }
}
