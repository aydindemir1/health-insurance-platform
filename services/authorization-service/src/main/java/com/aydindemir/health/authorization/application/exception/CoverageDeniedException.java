package com.aydindemir.health.authorization.application.exception;

public class CoverageDeniedException extends RuntimeException {
    private final String denialCode;

    public CoverageDeniedException(String denialCode, String reason) {
        super(reason);
        this.denialCode = denialCode;
    }

    public String denialCode() {
        return denialCode;
    }
}
