package com.dazzle.asklepios.client.setup.dto;

public record DiscountCodeValidateRequest(
        Long facilityId,
        String code,
        Long userId,
        String authorizationToken
) {
}
