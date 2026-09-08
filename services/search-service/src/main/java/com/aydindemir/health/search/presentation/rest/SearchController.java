package com.aydindemir.health.search.presentation.rest;

import com.aydindemir.health.search.application.dto.SearchPage;
import com.aydindemir.health.search.application.port.in.SearchRecordsUseCase;
import com.aydindemir.health.search.application.query.SearchRecordsQuery;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
class SearchController {
    private final SearchRecordsUseCase useCase;
    private final AuthenticatedActorMapper actors;

    SearchController(SearchRecordsUseCase useCase, AuthenticatedActorMapper actors) {
        this.useCase = useCase;
        this.actors = actors;
    }

    @GetMapping
    SearchPage search(
            JwtAuthenticationToken authentication,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return useCase.search(new SearchRecordsQuery(
                actors.from(authentication), q, type, status, providerId, page, size));
    }
}
