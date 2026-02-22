package com.dazzle.asklepios.service.dto.chiefComplain;

import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.enumeration.PatientCondition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChiefComplainUpdateDTO(
        @NotNull Long id,
        @NotEmpty String chiefComplaint,
        String provocation,
        String palliation,
        String quality,
        String region,
        @NotEmpty String severity,
        @NotNull Instant onsetDateTime,
        @NotEmpty String caseUnderstanding,
        PatientCondition patientCondition,
        @NotNull Boolean isTriage
) implements Serializable {

    public static ChiefComplainUpdateDTO ofEntity(ChiefComplain entity) {
        return new ChiefComplainUpdateDTO(
                entity.getId(),
                entity.getChiefComplaint(),
                entity.getProvocation(),
                entity.getPalliation(),
                entity.getQuality(),
                entity.getRegion(),
                entity.getSeverity(),
                entity.getOnsetDateTime(),
                entity.getCaseUnderstanding(),
                entity.getPatientCondition(),
                entity.getIsTriage()
        );
    }
}