package com.dazzle.asklepios.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyRenalFunctionUpdateDTO {

    @NotNull
    private Long id;

    private BigDecimal egfr;

    private BigDecimal creatinine;

    private BigDecimal bun;

    private BigDecimal hemoglobin;

    private BigDecimal potassium;

    private BigDecimal sodium;

    private BigDecimal calcium;

    private BigDecimal phosphorus;
}
