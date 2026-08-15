package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.dazzle.asklepios.integration.waseel.service.mapper.WaseelFactorJsonSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelApprovalItem(
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
        @JsonSerialize(using = WaseelFactorJsonSerializer.class)
        BigDecimal factor,
        BigDecimal taxPercent,
        BigDecimal patientSharePercent,
        BigDecimal net,
        BigDecimal tax,
        BigDecimal patientShare,
        BigDecimal payerShare,
        LocalDate startDate,
        LocalDate endDate,
        List<Integer> supportingInfoSequence,
        List<Integer> careTeamSequence,
        List<Integer> diagnosisSequence,
        String invoiceNo,
        List<Object> itemDetails
) {}
