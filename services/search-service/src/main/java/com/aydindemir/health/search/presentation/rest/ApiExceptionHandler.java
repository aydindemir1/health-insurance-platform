package com.aydindemir.health.search.presentation.rest;

import com.aydindemir.health.search.application.exception.ApplicationAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ProblemDetail forbidden(ApplicationAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Operation not permitted", exception.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.health-insurance.example/problems/" + title.toLowerCase().replace(' ', '-')));
        return problem;
    }
}
