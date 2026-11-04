package com.dazzle.asklepios.service.dto.billing;


import jakarta.validation.constraints.NotNull;

import java.util.Date;

public record ClaimEncounterCopyHospitalizationCreateDTO(
        String facility,
        String reason,
        String admissionType,
        Date dateOfAdmission,
        Integer lengthOfStayDays,
        String outcomes,
        String medicalInterventionsPerformed,
        @NotNull Boolean patientIsFree,
        String freeText
) {}