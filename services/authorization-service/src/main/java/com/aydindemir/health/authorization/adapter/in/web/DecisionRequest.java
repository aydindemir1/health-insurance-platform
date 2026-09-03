package com.aydindemir.health.authorization.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record DecisionRequest(@Size(max = 500) String reason) {
    record Rejection(@NotBlank @Size(max = 500) String reason) {
    }
}
