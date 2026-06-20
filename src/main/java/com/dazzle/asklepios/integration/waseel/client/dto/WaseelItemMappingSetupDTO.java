package com.dazzle.asklepios.integration.waseel.client.dto;


public record WaseelItemMappingSetupDTO(
        Long id,
        String itemType,
        Long sourceId,
        String itemCode,
        String itemName,
        Long sbsCatalogId,
        String sbsCode,
        String sbsDescription,
        Boolean requiresPreauth,
        Boolean isActive,
        String notes
) {}