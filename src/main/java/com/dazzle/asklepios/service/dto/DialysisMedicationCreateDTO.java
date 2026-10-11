package com.dazzle.asklepios.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisMedicationCreateDTO {

    @NotNull
    private Long dialysisSessionId;

    @NotNull
    private Long activeIngredientId;

    private Long dose;

    private String doseUnit;
}
