package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceListItemWaseelCodesDTO(
        String itemCode,
        String nonStandardCode,
        String sbsCode,
        String sbsDescription,
        String waseelItemType
) {
}
