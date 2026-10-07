package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessType;
import com.dazzle.asklepios.domain.enumeration.DialysisRegistrationStatus;
import com.dazzle.asklepios.domain.enumeration.DialysisScheduleDay;
import com.dazzle.asklepios.domain.enumeration.DialysisTreatmentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyTreatmentPlanCreateDTO {

    @NotNull
    private Long patientId;

    @NotNull
    private Long encounterId;

    private DialysisTreatmentType treatmentType;

    private Integer frequency;

    private List<DialysisScheduleDay> schedule;

    private BigDecimal dialysisDuration;

    private BigDecimal dryWeight;

    private BigDecimal targetWeight;

    private DialysisAccessType dialysisAccess;

    private DialysisAccessSite accessSite;

    private String otherAccessSite;

    private BigDecimal bloodFlowRate;

    private BigDecimal dialysateFlow;

    private String dialysate;

    private Long nephrologistId;

    private LocalDate startDate;

    private DialysisRegistrationStatus status;
}
