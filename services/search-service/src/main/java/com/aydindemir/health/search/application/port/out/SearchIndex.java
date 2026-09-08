package com.aydindemir.health.search.application.port.out;

import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.domain.model.RecordType;
import com.aydindemir.health.search.domain.model.SearchRecord;

import java.util.UUID;

public interface SearchIndex {
    void save(SearchRecord record);
    SearchPage search(String text, RecordType type, String status, UUID providerId, int page, int size);
}
