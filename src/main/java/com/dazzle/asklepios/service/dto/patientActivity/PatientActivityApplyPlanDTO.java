package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * The tasks of a plan as staff confirmed them in the preview: items may be dropped or changed,
 * but each one must still come from the plan.
 */
@Getter
@Setter
public class PatientActivityApplyPlanDTO {

    @NotNull
    private Long patientId;

    @NotBlank
    private String planCode;

    private Long encounterId;

    private Long appointmentId;

    @NotEmpty
    private List<@Valid PatientActivityApplyPlanItemDTO> items;
}
