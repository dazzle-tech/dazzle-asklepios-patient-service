package com.dazzle.asklepios.service.vm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisMedicationResponseVM {

    private Long id;

    private Long dialysisSessionId;

    private Long activeIngredientId;

    private Long dose;

    private String doseUnit;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;
}
