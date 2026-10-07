package com.dazzle.asklepios.service.vm;

import com.dazzle.asklepios.domain.enumeration.CkdStage;
import com.dazzle.asklepios.domain.enumeration.KidneyCondition;
import com.dazzle.asklepios.domain.enumeration.KidneyDiseaseCause;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyKidneyAssessmentResponseVM {

    private Long id;

    private Long patientId;

    private Long encounterId;

    private CkdStage ckdStage;

    private KidneyCondition kidneyCondition;

    private KidneyDiseaseCause causeOfKidneyDisease;

    private String otherCauseOfKidneyDisease;

    private Boolean diabetes;

    private Boolean hypertension;

    private Boolean proteinuria;

    private Boolean hematuria;

    private Boolean isActive;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;
}