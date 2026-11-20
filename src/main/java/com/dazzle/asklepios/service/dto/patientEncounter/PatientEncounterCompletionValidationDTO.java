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

        boolean vitalSigns,
        boolean bodyMeasurements,

        /** False when a Waseel insurance visit without investigations has no reason why none was done. */
        boolean investigationNotPerformedReason,

        boolean canComplete,

        List<String> missing,

        List<DiagnosticCheckoutApproval> diagnosticApprovals

) implements Serializable {
}