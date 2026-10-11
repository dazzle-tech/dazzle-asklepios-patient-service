package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.CkdStage;
import com.dazzle.asklepios.domain.enumeration.KidneyCondition;
import com.dazzle.asklepios.domain.enumeration.KidneyDiseaseCause;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyKidneyAssessmentCreateDTO {

    @NotNull
    private Long patientId;

    @NotNull
    private Long encounterId;

    private CkdStage ckdStage;

    private KidneyCondition kidneyCondition;

    private KidneyDiseaseCause causeOfKidneyDisease;

    private String otherCauseOfKidneyDisease;

    private Boolean diabetes;

    private Boolean hypertension;

    private Boolean proteinuria;

    private Boolean hematuria;
}