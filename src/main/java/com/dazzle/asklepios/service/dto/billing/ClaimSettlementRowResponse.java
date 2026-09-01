package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSettlementRowResponse(

        Long claimId,

        String settlementNo,

        Instant settlementDate,

        String insuranceCompany,

        String tpa,

        String claimNo,

        Instant claimDate,

        BigDecimal billedAmount,

        BigDecimal approvedAmount,

        BigDecimal rejectedAmount,

        BigDecimal patientShare,

        BigDecimal insuranceAmount,

        BigDecimal paidAmount,

        BigDecimal outstandingAmount,

        String settlementStatus

) implements Serializable {
}
