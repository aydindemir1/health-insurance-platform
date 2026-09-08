package com.aydindemir.health.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Component
public class ElasticsearchSearchIndex implements SearchIndex {
    private final ElasticsearchClient client;
    private final String indexName;

    public ElasticsearchSearchIndex(
            ElasticsearchClient client,
            @Value("${app.search.index-name:healthcare-operations-v1}") String indexName) {
        this.client = client;
        this.indexName = indexName;
    }

    @Override
    public void save(SearchRecord record) {
        try {
            ensureIndex();
            client.index(request -> request.index(indexName).id(record.id()).document(toDocument(record)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not index healthcare search record", exception);
        }
    }

    @Override
    public SearchPage search(
            String text, RecordType type, String status, UUID providerId, int page, int size) {
        try {
            ensureIndex();
            var response = client.search(request -> request
                    .index(indexName)
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

    private void ensureIndex() throws IOException {
        if (client.indices().exists(request -> request.index(indexName)).value()) return;
        client.indices().create(request -> request.index(indexName).mappings(mapping -> mapping
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
                .properties("occurredAt", property -> property.date(date -> date))));
    }

    private SearchIndexDocument toDocument(SearchRecord value) {
        return new SearchIndexDocument(
                value.id(), value.type().name(), value.sourceId(), value.preAuthorizationId(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status(), value.invoiceStatus(), value.invoiceNumber(), value.amount(),
                value.approvedAmount(), value.paidAmount(), value.currency(), value.reason(),
                value.occurredAt());
    }

    private SearchRecord toDomain(SearchIndexDocument value) {
        return new SearchRecord(
                value.id(), RecordType.valueOf(value.type()), value.sourceId(), value.preAuthorizationId(),
                value.memberId(), value.providerId(), value.policyNumber(), value.serviceCode(),
                value.status(), value.invoiceStatus(), value.invoiceNumber(), value.amount(),
                value.approvedAmount(), value.paidAmount(), value.currency(), value.reason(),
                value.occurredAt());
    }
}
