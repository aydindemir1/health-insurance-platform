package com.aydindemir.health.search.application.port.out;

import com.aydindemir.health.search.domain.model.SearchRecord;

public interface SearchRebuildIndex {
    String currentIndex();
    void createIndex(String indexName);
    void save(String indexName, SearchRecord record);
    void refresh(String indexName);
    long count(String indexName);
    void swapAlias(String expectedCurrentIndex, String targetIndex);
}
