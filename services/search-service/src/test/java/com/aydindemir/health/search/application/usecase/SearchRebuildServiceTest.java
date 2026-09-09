package com.aydindemir.health.search.application.usecase;

import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.search.application.exception.SearchRecoveryConflictException;
import com.aydindemir.health.search.application.port.out.SearchRebuildIndex;
import com.aydindemir.health.search.application.security.ActorContext;
import com.aydindemir.health.search.application.security.ApplicationRole;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRebuildServiceTest {
    @Test
    void countMismatchLeavesTheAliasUntouched() {
        var index = new FakeIndex();
        var service = new SearchRebuildService(index);
        var created = service.create(admin(), 2);

        assertThatThrownBy(() -> service.activate(admin(), created.runId(), 1))
                .isInstanceOf(SearchRecoveryConflictException.class);
        assertThat(index.currentIndex()).isEqualTo("healthcare-operations-v1");
    }

    @Test
    void rejectsNonAdministratorsBeforeCreatingAnIndex() {
        var index = new FakeIndex();
        var service = new SearchRebuildService(index);

        assertThatThrownBy(() -> service.create(
                new ActorContext("specialist", null, Set.of(ApplicationRole.INSURANCE_SPECIALIST)), 2))
                .isInstanceOf(ApplicationAccessDeniedException.class);
        assertThat(index.counts).isEmpty();
    }

    private ActorContext admin() {
        return new ActorContext("admin", null, Set.of(ApplicationRole.SYSTEM_ADMIN));
    }

    private static final class FakeIndex implements SearchRebuildIndex {
        private String current = "healthcare-operations-v1";
        private final Map<String, Long> counts = new HashMap<>();
        @Override public String currentIndex() { return current; }
        @Override public void createIndex(String indexName) { counts.put(indexName, 0L); }
        @Override public void save(String indexName, SearchRecord record) {
            counts.compute(indexName, (key, value) -> value == null ? 1 : value + 1);
        }
        @Override public void refresh(String indexName) { }
        @Override public long count(String indexName) { return counts.getOrDefault(indexName, 0L); }
        @Override public void swapAlias(String expectedCurrentIndex, String targetIndex) {
            if (!current.equals(expectedCurrentIndex)) throw new AssertionError("unexpected predecessor");
            current = targetIndex;
        }
    }
}
