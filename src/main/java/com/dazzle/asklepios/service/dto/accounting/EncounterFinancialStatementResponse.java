package com.dazzle.asklepios.service.dto.accounting;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EncounterFinancialStatementResponse(

        Header header,

        VisitPatient visitPatient,

        CoveragePayer coveragePayer,

        List<ServiceLine> serviceLines,

        InvoiceBreakdown invoiceBreakdown,

        PatientSettlement patientSettlement,

        InsuranceSplit insuranceSplit,

        List<ReceiptRow> receipts,

        ClaimFinancial claimFinancial,

        List<TimelineRow> timeline,

        FinalSettlement finalSettlement,

        List<AuditRow> auditTrail,

        StatementFooter footer

) implements Serializable {

    public record Header(

            Long encounterId,

            String encounterNumber,

            String patientName,

            String medicalRecordNumber,

            Instant encounterDateTime,

            String visitType,

            String financialStatus,

            String invoiceNumber,

            Long invoiceId,

            String currency,

            BigDecimal grossServices,

            BigDecimal vatAmount,

            BigDecimal patientBilled,

            BigDecimal collected,

            BigDecimal outstanding

    ) implements Serializable {
    }

    public record VisitPatient(

            String patientName,

            String medicalRecordNumber,

            String nationalId,

            Long facilityId,

            Long departmentId,

            Long practitionerId,

            String visitType,

            Instant encounterDateTime,

            LocalDate encounterDate,

            LocalTime encounterTime,

            String invoiceNumber

    ) implements Serializable {
    }

    public record CoveragePayer(

            String payerName,

            String coverageType,

            String memberId,

            String policyNumber,

            String eligibility,

            String authorization,

            String claimNumber,

            Long claimId,

            String financialStatus

    ) implements Serializable {
    }

    public record ServiceLine(

            Long chargeLineId,

            String serviceCode,

            String serviceName,

            BigDecimal quantity,

            BigDecimal unitPrice,

            BigDecimal grossAmount,

            BigDecimal discountAmount,

            BigDecimal netAmount,

            BigDecimal deductibleAmount,

            BigDecimal copayAmount,

            BigDecimal nonCoveredAmount,

            BigDecimal patientResponsibility,

            BigDecimal insuranceShare,

            BigDecimal vatAmount,

            BigDecimal lineTotal

    ) implements Serializable {
    }

    public record InvoiceBreakdown(

            BigDecimal grossServices,

            BigDecimal discountAmount,

            BigDecimal netServices,

            BigDecimal taxableAmount,

            BigDecimal vatAmount,

            BigDecimal totalBilled

    ) implements Serializable {
    }

    public record PatientSettlement(

            BigDecimal patientBilled,

            BigDecimal collectedOnInvoice,

            BigDecimal walletReserved,

            BigDecimal refundsAdjustments,

            BigDecimal outstanding

    ) implements Serializable {
    }

    public record InsuranceSplit(

            BigDecimal eligibleAmount,

            BigDecimal deductibleAmount,

            BigDecimal patientCopayment,

            BigDecimal insuranceShare,

            BigDecimal nonCoveredAmount,

            BigDecimal insuranceResponsibility

    ) implements Serializable {
    }

    public record ReceiptRow(

            Instant paymentDate,

            String receiptNumber,

            String paymentMethod,

            String payer,

            String status,

            BigDecimal amount

    ) implements Serializable {
    }

    public record ClaimFinancial(

            Long claimId,

            String claimNumber,

            String claimStatus,

            String rejectionReason,

            BigDecimal submittedAmount,

            BigDecimal approvedAmount,

            BigDecimal rejectedAmount,

            BigDecimal resubmittedAmount,

            BigDecimal finalApprovedAmount,

            BigDecimal insurancePaymentReceived,

            BigDecimal insuranceOutstanding

    ) implements Serializable {
    }

    public record TimelineRow(

            Instant transactionDate,

            String transactionType,

            String reference,

            BigDecimal debit,

            BigDecimal credit,

            BigDecimal runningBalance

    ) implements Serializable {
    }

    public record SettlementParty(

            String party,

            BigDecimal billed,

            BigDecimal collected,

            BigDecimal outstanding

    ) implements Serializable {
    }

    public record FinalSettlement(

            BigDecimal grossCharges,

            BigDecimal patientBilled,

            BigDecimal insuranceBilled,

            BigDecimal patientCollected,

            BigDecimal insuranceCollected,

            BigDecimal totalCollected,

            BigDecimal patientOutstanding,

            BigDecimal insuranceOutstanding,

            BigDecimal visitOutstanding,

            String overallFinancialStatus,

            List<SettlementParty> parties

    ) implements Serializable {
    }

    public record AuditRow(

            Instant eventDate,

            String event,

            String reference,

            String user,

            String previousValue,

            String newValue,

            String reason

    ) implements Serializable {
    }

    public record StatementFooter(

            String preparedBy,

            String finalizedBy,

            Instant finalizedDate,

            String patientPaymentStatus,

            String insurancePaymentStatus,

            String overallSettlementStatus,

            String statementLifecycle,

            Instant generatedDate

    ) implements Serializable {
    }
}
