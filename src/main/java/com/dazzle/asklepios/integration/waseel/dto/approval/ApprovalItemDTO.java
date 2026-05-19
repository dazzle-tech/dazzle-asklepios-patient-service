package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.math.BigDecimal;
import java.util.List;

public record ApprovalItemDTO(
        Integer sequence,
        String type,
        String itemCode,
        String itemDescription,
        String nonStandardCode,
        String nonStandardDesc,
        Boolean isPackage,
        Boolean isMaternity,
        String bodySite,
        String subSite,
        Integer quantity,
        String quantityCode,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal factor,
        BigDecimal taxPercent,
        BigDecimal patientSharePercent,
        BigDecimal tax,
        BigDecimal net,
        BigDecimal patientShare,
        BigDecimal payerShare,
        String startDate,
        String endDate,
        java.util.List<Integer> supportingInfoSequence,
        java.util.List<Integer> careTeamSequence,
        java.util.List<Integer> diagnosisSequence,
        String invoiceNo,
        List<Object> itemDetails
) {}