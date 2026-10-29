package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientActivityChecklistDTO {

    @NotNull
    @Min(0)
    private Integer index;

    private boolean done;
}
