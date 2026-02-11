package com.dazzle.asklepios.service.dto.chiefComplain;

import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.enumeration.PatientCondition;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChiefComplainCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
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

    public static ChiefComplainCreateDTO ofEntity(ChiefComplain entity) {
        return new ChiefComplainCreateDTO(
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounterId(),
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