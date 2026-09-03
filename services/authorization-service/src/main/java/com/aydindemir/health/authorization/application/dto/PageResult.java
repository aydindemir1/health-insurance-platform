package com.aydindemir.health.authorization.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResult {
        content = List.copyOf(Objects.requireNonNull(content));
        if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Invalid page metadata");
        }
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                content.stream().map(mapper).toList(),
                page,
                size,
                totalElements,
                totalPages);
    }
}
