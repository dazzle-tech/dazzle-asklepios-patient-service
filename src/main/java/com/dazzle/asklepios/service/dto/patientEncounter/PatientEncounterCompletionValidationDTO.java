package com.dazzle.asklepios.service.dto.patientEncounter;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

import java.io.Serializable;
import java.util.List;

public record PatientEncounterCompletionValidationDTO(

        Long encounterId,

        BillingCoverageType coverageType,

        Long patientInsuranceId,

        boolean insuranceVisit,

        boolean chiefComplaint,

        boolean historyOfPresentIllness,
        boolean physicalExaminationSummary,
        boolean primaryDiagnosis,
        boolean assessment,
        boolean treatmentPlan,

        boolean medicalHistory,
        boolean surgicalHistory,
        boolean socialHistory,

        boolean progressNotes,
        boolean vitalSigns,
        boolean bodyMeasurements,

        boolean canComplete,

        List<String> missing

) implements Serializable {
}