package com.aydindemir.health.search.application.exception;

public class ApplicationAccessDeniedException extends RuntimeException {
    public ApplicationAccessDeniedException(String message) { super(message); }
}
