package com.aydindemir.health.search.infrastructure.configuration;

import com.aydindemir.health.search.application.port.out.SearchIndex;
import com.aydindemir.health.search.application.usecase.SearchApplicationService;
import com.aydindemir.health.search.application.usecase.SearchRebuildService;
import com.aydindemir.health.search.application.port.in.SearchRebuildUseCase;
import com.aydindemir.health.search.application.port.out.SearchRebuildIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    SearchApplicationService searchApplicationService(SearchIndex index) {
        return new SearchApplicationService(index);
    }

    @Bean
    SearchRebuildUseCase searchRebuildUseCase(SearchRebuildIndex index) {
        return new SearchRebuildService(index);
    }
}
