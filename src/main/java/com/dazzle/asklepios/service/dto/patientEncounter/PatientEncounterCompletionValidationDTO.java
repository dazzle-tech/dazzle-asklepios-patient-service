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

        /** Patient History subsections: each needs at least one ACTIVE record of its own. */
        boolean patientProblems,
        boolean familyHistory,
        boolean hospitalizations,
        boolean currentMedication,

        /** False when a Waseel insurance visit without investigations has no reason why none was done. */
        boolean investigationNotPerformedReason,

        boolean canComplete,

        List<String> missing,

        /** Every requirement with its stable code (CHIEF_COMPLAINT, VITAL_SIGNS, ...) and completion state. */
        List<Requirement> requirements,

        List<DiagnosticCheckoutApproval> diagnosticApprovals

) implements Serializable {

    public record Requirement(String code, String label, boolean completed) implements Serializable {
    }
}