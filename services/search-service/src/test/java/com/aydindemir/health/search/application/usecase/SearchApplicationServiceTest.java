package com.aydindemir.health.search.application.usecase;

import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.application.query.SearchRecordsQuery;
import com.aydindemir.health.search.application.security.ActorContext;
import com.aydindemir.health.search.application.security.ApplicationRole;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchApplicationServiceTest {
    private final RecordingIndex index = new RecordingIndex();
    private final SearchApplicationService service = new SearchApplicationService(index);

    @Test
    void forcesHospitalUserSearchToTokenProvider() {
        UUID providerId = UUID.randomUUID();
        service.search(query(hospital(providerId), null));
        assertThat(index.providerId).isEqualTo(providerId);
    }

    @Test
    void rejectsCrossProviderHospitalSearch() {
        assertThatThrownBy(() -> service.search(query(hospital(UUID.randomUUID()), UUID.randomUUID())))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    @Test
    void allowsSpecialistToSearchAcrossProviders() {
        service.search(query(new ActorContext("specialist", null,
                Set.of(ApplicationRole.INSURANCE_SPECIALIST)), null));
        assertThat(index.providerId).isNull();
    }

    private SearchRecordsQuery query(ActorContext actor, UUID providerId) {
        return new SearchRecordsQuery(actor, null, "CLAIM", null, providerId, 0, 20);
    }

    private ActorContext hospital(UUID providerId) {
        return new ActorContext("hospital", providerId, Set.of(ApplicationRole.HOSPITAL_USER));
    }

    private static final class RecordingIndex implements SearchIndex {
        private UUID providerId;
        @Override public void save(SearchRecord record) {}
        @Override public SearchPage search(String text, RecordType type, String status,
                                           UUID providerId, int page, int size) {
            this.providerId = providerId;
            return new SearchPage(List.of(), page, size, 0);
        }
    }
}
