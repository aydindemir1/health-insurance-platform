package com.aydindemir.health.authorization.presentation.rest;

import com.aydindemir.health.authorization.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.authorization.application.exception.PreAuthorizationNotFoundException;
import com.aydindemir.health.authorization.application.exception.PreAuthorizationStateConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ProblemDetail handleAccessDenied(ApplicationAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Operation not permitted", exception.getMessage());
    }

    @ExceptionHandler(PreAuthorizationNotFoundException.class)
    ProblemDetail handleNotFound(PreAuthorizationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Pre-authorization not found", exception.getMessage());
    }

    @ExceptionHandler(PreAuthorizationStateConflictException.class)
    ProblemDetail handleBusinessRule(PreAuthorizationStateConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Business rule violation", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
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
