package com.dazzle.asklepios.integration.waseel.client.dto;

public record LanguageResponseVM(
        Long id,
        String langKey,
        String langName,
        String direction,
        String details
) {
}
