package com.aydindemir.health.search.application.port.in;

import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.query.SearchRecordsQuery;

public interface SearchRecordsUseCase { SearchPage search(SearchRecordsQuery query); }
