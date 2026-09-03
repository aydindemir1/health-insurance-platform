package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.dto.PageResult;
import com.aydindemir.health.authorization.application.dto.PreAuthorizationResult;

import java.util.List;

record PreAuthorizationPageResponse(
        List<PreAuthorizationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    static PreAuthorizationPageResponse from(
            PageResult<PreAuthorizationResult> source) {
        return new PreAuthorizationPageResponse(
                source.content().stream().map(PreAuthorizationResponse::from).toList(),
                source.page(),
                source.size(),
                source.totalElements(),
                source.totalPages(),
                source.page() == 0,
                source.totalPages() == 0 || source.page() >= source.totalPages() - 1);
    }
}
