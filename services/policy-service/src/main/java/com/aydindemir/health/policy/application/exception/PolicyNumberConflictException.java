package com.aydindemir.health.policy.application.exception;

public class PolicyNumberConflictException extends RuntimeException {
    public PolicyNumberConflictException(String policyNumber) {
        super("Policy number already exists: " + policyNumber);
    }

    public PolicyNumberConflictException(String policyNumber, Throwable cause) {
        super("Policy number already exists: " + policyNumber, cause);
    }
}
