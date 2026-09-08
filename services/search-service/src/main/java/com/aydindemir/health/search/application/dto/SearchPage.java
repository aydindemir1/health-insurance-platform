package com.aydindemir.health.search.application.dto;

import com.aydindemir.health.search.domain.model.SearchRecord;
import java.util.List;

public record SearchPage(List<SearchRecord> content, int page, int size, long totalElements) {
    public SearchPage { content = List.copyOf(content); }
}
