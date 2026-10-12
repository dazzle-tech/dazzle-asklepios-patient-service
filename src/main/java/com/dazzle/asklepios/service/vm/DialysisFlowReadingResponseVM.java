package com.dazzle.asklepios.service.vm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisFlowReadingResponseVM {

    private Long id;

    private Long dialysisSessionId;

    private LocalTime time;

    private Integer bloodPressureSystolic;

    private Integer bloodPressureDiastolic;

    private Integer pulse;

    private BigDecimal ufRate;

    private BigDecimal ufRemoved;

    private BigDecimal arterialPressure;

    private BigDecimal venousPressure;

    private BigDecimal transmembranePressure;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;
}
