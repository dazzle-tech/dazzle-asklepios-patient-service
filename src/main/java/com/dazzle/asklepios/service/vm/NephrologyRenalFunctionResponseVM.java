package com.dazzle.asklepios.service.vm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyRenalFunctionResponseVM {

    private Long id;

    private Long patientId;

    private Long encounterId;

    private BigDecimal egfr;

    private BigDecimal creatinine;

    private BigDecimal bun;

    private BigDecimal hemoglobin;

    private BigDecimal potassium;

    private BigDecimal sodium;

    private BigDecimal calcium;

    private BigDecimal phosphorus;

    private Boolean isActive;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;
}
