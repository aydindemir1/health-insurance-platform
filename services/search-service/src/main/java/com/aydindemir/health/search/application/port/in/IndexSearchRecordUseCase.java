package com.aydindemir.health.search.application.port.in;

import com.aydindemir.health.search.domain.model.SearchRecord;

public interface IndexSearchRecordUseCase { void index(SearchRecord record); }
