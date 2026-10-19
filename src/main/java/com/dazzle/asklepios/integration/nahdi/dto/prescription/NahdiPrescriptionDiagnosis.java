package com.dazzle.asklepios.integration.nahdi.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entry of the {@code pres_Diagnosis} section of the Nahdi SendPrescription request.
 */
public record NahdiPrescriptionDiagnosis(
        @JsonProperty("type") String type,
        @JsonProperty("code") String code,
        @JsonProperty("description") String description
) {}
