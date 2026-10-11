package com.dazzle.asklepios.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisFlowReadingCreateDTO {

    @NotNull
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
}
