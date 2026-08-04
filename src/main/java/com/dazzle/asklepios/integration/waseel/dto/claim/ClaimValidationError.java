package com.dazzle.asklepios.integration.waseel.dto.claim;

public record ClaimValidationError(
        String code,
        String message,
        String section
) {}
