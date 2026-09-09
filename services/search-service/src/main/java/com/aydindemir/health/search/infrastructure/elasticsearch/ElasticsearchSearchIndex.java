package com.aydindemir.health.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;
import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.application.port.out.SearchRebuildIndex;
import com.aydindemir.health.search.application.exception.SearchRecoveryConflictException;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Component
public class ElasticsearchSearchIndex implements SearchIndex, SearchRebuildIndex {
    private final ElasticsearchClient client;
    private final String aliasName;
    private final String initialIndexName;

    public ElasticsearchSearchIndex(
            ElasticsearchClient client,
            @Value("${app.search.alias-name:healthcare-operations}") String aliasName,
            @Value("${app.search.initial-index-name:healthcare-operations-v1}") String initialIndexName) {
        this.client = client;
        this.aliasName = aliasName;
        this.initialIndexName = initialIndexName;
    }

    @PostConstruct
    void initializeIndexAlias() {
        try {
            ensureIndex();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize healthcare search index alias", exception);
        }
    }

    @Override
    public void save(SearchRecord record) {
        try {
            ensureIndex();
            upsert(aliasName, record);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not index healthcare search record", exception);
        }
    }

    @Override
    public String currentIndex() {
        try {
            ensureIndex();
            var indices = client.indices().getAlias(request -> request.name(aliasName)).aliases().keySet();
            if (indices.size() != 1) {
                throw new SearchRecoveryConflictException(
                        "Search alias must resolve to exactly one index but resolved to " + indices.size());
            }
            return indices.iterator().next();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not resolve healthcare search alias", exception);
        }
    }

    @Override
    public void createIndex(String indexName) {
        try {
            if (client.indices().exists(request -> request.index(indexName)).value()) {
                throw new SearchRecoveryConflictException("Candidate index already exists: " + indexName);
            }
            createPhysicalIndex(indexName, false);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create search rebuild candidate", exception);
        }
    }

    @Override
    public void save(String indexName, SearchRecord record) {
        try {
            upsert(indexName, record);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not index search rebuild record", exception);
        }
    }

    @Override
    public long count(String indexName) {
        try {
            return client.count(request -> request.index(indexName)).count();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not count search rebuild records", exception);
        }
    }

    @Override
    public void refresh(String indexName) {
        try {
            client.indices().refresh(request -> request.index(indexName));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not refresh search rebuild candidate", exception);
        }
    }

    @Override
    public synchronized void swapAlias(String expectedCurrentIndex, String targetIndex) {
        String actual = currentIndex();
        if (!actual.equals(expectedCurrentIndex)) {
            throw new SearchRecoveryConflictException(
                    "Alias changed concurrently; expected " + expectedCurrentIndex + " but found " + actual);
        }
        try {
            client.indices().updateAliases(request -> request
                    .actions(action -> action.remove(remove -> remove
                            .index(expectedCurrentIndex).alias(aliasName)))
                    .actions(action -> action.add(add -> add
                            .index(targetIndex).alias(aliasName).isWriteIndex(true))));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not atomically swap healthcare search alias", exception);
        }
    }

    @Override
    public SearchPage search(
            String text, RecordType type, String status, UUID providerId, int page, int size) {
        try {
            ensureIndex();
            var response = client.search(request -> request
                    .index(aliasName)
                    .from(page * size)
                    .size(size)
                    .sort(sort -> sort.field(field -> field.field("occurredAt").order(SortOrder.Desc)))
                    .query(query -> query.bool(bool -> {
                        if (text != null) {
                            bool.must(clause -> clause.simpleQueryString(simple -> simple
                                    .query(text)
                                    .fields("policyNumber", "serviceCode", "invoiceNumber", "reason")));
                        }
                        if (type != null) {
                            bool.filter(clause -> clause.term(term -> term.field("type").value(type.name())));
                        }
                        if (status != null) {
                            bool.filter(clause -> clause.term(term -> term.field("status").value(
                                    status.toUpperCase(Locale.ROOT))));
                        }
                        if (providerId != null) {
                            bool.filter(clause -> clause.term(term -> term.field("providerId").value(
                                    providerId.toString())));
                        }
                        return bool;
                    })), SearchIndexDocument.class);
            var content = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .filter(java.util.Objects::nonNull)
                    .map(this::toDomain)
                    .toList();
            long total = response.hits().total() == null ? content.size() : response.hits().total().value();
            return new SearchPage(content, page, size, total);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not search healthcare records", exception);
        }
    }

    private synchronized void ensureIndex() throws IOException {
        if (client.indices().existsAlias(request -> request.name(aliasName)).value()) return;
        if (client.indices().exists(request -> request.index(initialIndexName)).value()) {
            client.indices().updateAliases(request -> request.actions(action -> action.add(add -> add
                    .index(initialIndexName)
                    .alias(aliasName)
                    .isWriteIndex(true))));
            return;
        }
        createPhysicalIndex(initialIndexName, true);
    }

    private void createPhysicalIndex(String indexName, boolean attachAlias) throws IOException {
        client.indices().create(request -> {
            request.index(indexName);
            if (attachAlias) request.aliases(aliasName, alias -> alias.isWriteIndex(true));
            return request.mappings(mapping -> mapping
                .properties("type", property -> property.keyword(keyword -> keyword))
                .properties("sourceId", property -> property.keyword(keyword -> keyword))
                .properties("preAuthorizationId", property -> property.keyword(keyword -> keyword))
                .properties("memberId", property -> property.keyword(keyword -> keyword))
                .properties("providerId", property -> property.keyword(keyword -> keyword))
                .properties("policyNumber", property -> property.text(field -> field
                        .fields("keyword", keyword -> keyword.keyword(value -> value))))
                .properties("serviceCode", property -> property.text(field -> field
                        .fields("keyword", keyword -> keyword.keyword(value -> value))))
                .properties("status", property -> property.keyword(keyword -> keyword))
                .properties("invoiceStatus", property -> property.keyword(keyword -> keyword))
                .properties("invoiceNumber", property -> property.text(field -> field
                        .fields("keyword", keyword -> keyword.keyword(value -> value))))
                .properties("currency", property -> property.keyword(keyword -> keyword))
                .properties("reason", property -> property.text(text -> text))
                .properties("sourceRevision", property -> property.long_(number -> number))
                .properties("occurredAt", property -> property.date(date -> date)));
        });
    }

    private void upsert(String indexName, SearchRecord record) throws IOException {
        SearchIndexDocument document = toDocument(record);
        client.update(request -> request
                .index(indexName)
                .id(record.id())
                .retryOnConflict(3)
                .scriptedUpsert(true)
                .script(script -> script
                        .lang("painless")
                        .source(source -> source.scriptString(
                                "if (ctx._source.sourceRevision == null || "
                                        + "params.sourceRevision >= ctx._source.sourceRevision) "
                                        + "{ ctx._source = params.document } else { ctx.op = 'noop' }"))
                        .params("sourceRevision", JsonData.of(record.sourceRevision()))
                        .params("document", JsonData.of(
                                document, client._transport().jsonpMapper())))
                .upsert(document), SearchIndexDocument.class);
    }

    private SearchIndexDocument toDocument(SearchRecord value) {
        return new SearchIndexDocument(
                value.id(), value.type().name(), value.sourceId(), value.preAuthorizationId(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status(), value.invoiceStatus(), value.invoiceNumber(), value.amount(),
                value.approvedAmount(), value.paidAmount(), value.currency(), value.reason(),
                value.sourceRevision(), value.occurredAt());
    }

    private SearchRecord toDomain(SearchIndexDocument value) {
        return new SearchRecord(
                value.id(), RecordType.valueOf(value.type()), value.sourceId(), value.preAuthorizationId(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status(), value.invoiceStatus(), value.invoiceNumber(), value.amount(),
                value.approvedAmount(), value.paidAmount(), value.currency(), value.reason(),
                value.sourceRevision() == null ? 1L : value.sourceRevision(), value.occurredAt());
    }
}
