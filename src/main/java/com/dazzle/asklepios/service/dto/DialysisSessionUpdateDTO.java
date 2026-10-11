package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessType;
import com.dazzle.asklepios.domain.enumeration.DialysisAnticoagulation;
import com.dazzle.asklepios.domain.enumeration.DialysisComplication;
import com.dazzle.asklepios.domain.enumeration.DialysisDisposition;
import com.dazzle.asklepios.domain.enumeration.DialysisShift;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisSessionUpdateDTO {

    @NotNull
    private Long id;

    private String chairStation;

    private String machine;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    private DialysisShift shift;

    private Long assignedNurseId;

    private Long nephrologistId;

    private BigDecimal preWeight;

    private BigDecimal dryWeight;

    private BigDecimal weightGain;

    private BigDecimal interdialyticWeightGain;

    private Integer bloodPressureSystolic;

    private Integer bloodPressureDiastolic;

    private Integer heartRate;

    private BigDecimal temperature;

    private Integer respiratoryRate;

    private BigDecimal oxygenSaturation;

    private String symptoms;

    private String edema;

    private String preAccessCondition;

    private String generalCondition;

    private BigDecimal dialysisDuration;

    private BigDecimal bloodFlowRate;

    private BigDecimal dialysateFlowRate;

    private BigDecimal ultrafiltrationGoal;

    private String dialysateComposition;

    private BigDecimal sodium;

    private BigDecimal potassium;

    private BigDecimal calcium;

    private BigDecimal dialysisTemperature;

    private BigDecimal heparinDose;

    private DialysisAnticoagulation anticoagulation;

    private String otherAnticoagulation;

    private BigDecimal targetDryWeight;

    private DialysisAccessType accessType;

    private DialysisAccessSite accessSite;

    private String otherAccessSite;

    private Boolean infection;

    private Boolean bleeding;

    private String thrill;

    private String bruit;

    private String dressing;

    private String catheterCondition;

    private BigDecimal postWeight;

    private Integer postBloodPressureSystolic;

    private Integer postBloodPressureDiastolic;

    private Integer postPulse;

    private BigDecimal postTemperature;

    private BigDecimal totalUfRemoved;

    private BigDecimal actualTreatmentDuration;

    private String postAccessCondition;

    private DialysisDisposition disposition;

    private String postComplications;

    private String patientCondition;

    private BigDecimal targetUf;

    private BigDecimal actualUf;

    private BigDecimal ufDifference;

    private List<DialysisComplication> complications;

    private String complicationNotes;
}
