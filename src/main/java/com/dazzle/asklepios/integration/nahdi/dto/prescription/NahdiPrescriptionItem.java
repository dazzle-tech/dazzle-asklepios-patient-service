package com.dazzle.asklepios.integration.nahdi.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Entry of the {@code pres_Item} section of the Nahdi SendPrescription request.
 */
@Builder
public record NahdiPrescriptionItem(
        @JsonProperty("line_No") String lineNo,
        @JsonProperty("code_Type") String codeType,
        @JsonProperty("code") String code,
        @JsonProperty("description") String description,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("dose") String dose,
        @JsonProperty("price") String price,
        @JsonProperty("APP_STATUS") String appStatus
) {}
