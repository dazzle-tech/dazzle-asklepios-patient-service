package com.dazzle.asklepios.service.vm;

import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessType;
import com.dazzle.asklepios.domain.enumeration.DialysisRegistrationStatus;
import com.dazzle.asklepios.domain.enumeration.DialysisScheduleDay;
import com.dazzle.asklepios.domain.enumeration.DialysisTreatmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyTreatmentPlanResponseVM {

    private Long id;

    private Long patientId;

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

    private Boolean isActive;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;
}
