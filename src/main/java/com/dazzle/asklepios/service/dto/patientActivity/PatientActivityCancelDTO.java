package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientActivityCancelDTO {

    @NotBlank
    @Size(max = 255)
    private String cancellationReason;
}
