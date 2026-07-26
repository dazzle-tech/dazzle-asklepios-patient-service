package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record EncounterInvoiceDetailsResponse(

        Long encounterId,

        String encounterNumber,

        LocalDate encounterDate,

        Long departmentId,

        EncounterType encounterType,

        EncounterBillingStatus billingStatus,

        Instant financiallyClosedAt,

        String financiallyClosedBy,

        String coverageType,

        String eligibilityReference,

        BillingEligibilitySnapshotResponse eligibilitySnapshot,

        PatientInvoiceHeader patient,

        EncounterBillingSummary billingSummary

) implements Serializable {

    public record PatientInvoiceHeader(

            Long patientId,

            String medicalRecordNumber,

            String fullName,

            String nationalId,

            String mobileNumber

    ) implements Serializable {
    }
}
