package com.aydindemir.health.claims.presentation.rest;

import com.aydindemir.health.claims.application.exception.ApplicationAccessDeniedException;
import com.aydindemir.health.claims.application.exception.ApprovedPreAuthorizationRequiredException;
import com.aydindemir.health.claims.application.exception.AuthorizationServiceUnavailableException;
import com.aydindemir.health.claims.application.exception.ClaimNotFoundException;
import com.aydindemir.health.claims.application.exception.ClaimsBillingStateConflictException;
import com.aydindemir.health.claims.application.exception.ConcurrentClaimsBillingUpdateException;
import com.aydindemir.health.claims.application.exception.DuplicateClaimException;
import com.aydindemir.health.claims.application.exception.InvoiceNotFoundException;
import com.aydindemir.health.claims.application.exception.InvoiceNumberConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ProblemDetail forbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "Operation not permitted", exception.getMessage());
    }

    @ExceptionHandler({ClaimNotFoundException.class, InvoiceNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Required resource not found", exception.getMessage());
    }

    @ExceptionHandler({ApprovedPreAuthorizationRequiredException.class,
            DuplicateClaimException.class, InvoiceNumberConflictException.class,
            ConcurrentClaimsBillingUpdateException.class, ClaimsBillingStateConflictException.class})
    ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Operation conflicts with current state", exception.getMessage());
    }

    @ExceptionHandler(AuthorizationServiceUnavailableException.class)
    ProblemDetail unavailable(RuntimeException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Authorization service unavailable", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed",
                "One or more request fields are invalid");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, ignored) -> first)));
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
