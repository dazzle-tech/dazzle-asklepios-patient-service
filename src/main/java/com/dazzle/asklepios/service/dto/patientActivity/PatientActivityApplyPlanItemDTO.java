package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PatientActivityApplyPlanItemDTO {

    @NotBlank
    private String activityType;

    @NotNull
    private Instant dueAt;

    private PatientActivityPriority priority;

    private List<String> assignedRoles;

    @Size(max = 50)
    private String assignedTo;

    @Size(max = 2000)
    private String note;
}
