package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PatientActivityCreateDTO {

    @NotNull
    private Long patientId;

    private Long encounterId;

    private Long appointmentId;

    @NotNull
    private PatientActivityCategory category;

    @NotNull
    private PatientActivityType activityType;

    @NotNull
    private PatientActivityStatus status;

    private PatientActivityPriority priority;

    @NotBlank
    @Size(max = 2000)
    private String note;

    private Instant dueAt;
}
