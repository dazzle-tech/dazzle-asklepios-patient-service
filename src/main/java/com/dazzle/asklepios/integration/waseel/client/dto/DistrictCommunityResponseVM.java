package com.dazzle.asklepios.integration.waseel.client.dto;

public record DistrictCommunityResponseVM(
        Long id,
        String name,
        Long districtId,
        Boolean isActive
) {
}