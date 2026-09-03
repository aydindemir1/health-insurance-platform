package com.aydindemir.health.authorization.adapter.in.web;

import com.aydindemir.health.authorization.application.PreAuthorizationNotFoundException;
import com.aydindemir.health.authorization.domain.InvalidPreAuthorizationStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(PreAuthorizationNotFoundException.class)
    ProblemDetail handleNotFound(PreAuthorizationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Pre-authorization not found", exception.getMessage());
    }

    @ExceptionHandler({InvalidPreAuthorizationStateException.class, IllegalArgumentException.class})
    ProblemDetail handleBusinessRule(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Business rule violation", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "One or more request fields are invalid");
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        detail.setProperty("errors", errors);
        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.health-insurance.example/problems/" +
                title.toLowerCase().replace(' ', '-')));
        return problem;
    }
}
