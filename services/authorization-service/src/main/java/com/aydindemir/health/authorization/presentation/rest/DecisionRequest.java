package com.aydindemir.health.authorization.presentation.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record DecisionRequest(@Size(max = 500) String reason) {
    record Rejection(@NotBlank @Size(max = 500) String reason) {
    }
}
