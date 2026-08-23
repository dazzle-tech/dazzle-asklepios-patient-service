package com.dazzle.asklepios.service.dto.accounting;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PatientFinancialDashboardResponse(

        Long patientId,

        String medicalRecordNumber,

        String patientName,

        String nationalId,

        String currency,

        DashboardTotals totals,

        List<EncounterFinancialRow> encounters

) implements Serializable {

    public record DashboardTotals(

            BigDecimal grossCharges,

            BigDecimal patientResponsibility,

            BigDecimal insuranceShare,

            BigDecimal totalCollected,

            BigDecimal outstanding

    ) implements Serializable {
    }

    public record EncounterFinancialRow(

            Long encounterId,

            String encounterNumber,

            LocalDate encounterDate,

            LocalTime encounterTime,

            Instant encounterDateTime,

            String visitType,

            Long facilityId,

            Long departmentId,

            Long practitionerId,

            String coverageType,

            BigDecimal grossCharges,

            BigDecimal patientResponsibility,

            BigDecimal insuranceShare,

            BigDecimal collected,

            BigDecimal outstanding,

            String financialStatus,

            String invoiceNumber,

            Long invoiceId,

            String claimNumber,

            Long claimId

    ) implements Serializable {
    }
}
