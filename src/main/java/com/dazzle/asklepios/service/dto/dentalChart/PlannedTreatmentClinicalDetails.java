package com.dazzle.asklepios.service.dto.dentalChart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Snapshot of clinical values entered on a planned treatment.
 * Persisted as a single JSON document in {@code clinical_details}
 * (not as four separate table columns).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannedTreatmentClinicalDetails(
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial
) implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PlannedTreatmentClinicalDetails empty() {
        return new PlannedTreatmentClinicalDetails(null, null, null, null);
    }

    public boolean isBlank() {
        return (anesthesiaUsed == null || anesthesiaUsed.isBlank())
                && dose == null
                && (unit == null || unit.isBlank())
                && (fillingMaterial == null || fillingMaterial.isBlank());
    }

    public String toJson() {
        if (isBlank()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize clinical details", ex);
        }
    }

    public static PlannedTreatmentClinicalDetails fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            PlannedTreatmentClinicalDetails parsed =
                    MAPPER.readValue(json, PlannedTreatmentClinicalDetails.class);
            return parsed != null ? parsed : empty();
        } catch (JsonProcessingException ex) {
            return empty();
        }
    }

    public static PlannedTreatmentClinicalDetails of(
            String anesthesiaUsed,
            BigDecimal dose,
            String unit,
            String fillingMaterial
    ) {
        String anesthesia = blankToNull(anesthesiaUsed);
        String unitValue = blankToNull(unit);
        String material = blankToNull(fillingMaterial);
        return new PlannedTreatmentClinicalDetails(anesthesia, dose, unitValue, material);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
