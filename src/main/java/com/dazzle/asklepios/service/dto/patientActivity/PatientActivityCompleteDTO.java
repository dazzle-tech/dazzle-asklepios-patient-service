package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientActivityCompleteDTO {

    @Size(max = 100)
    private String outcome;

    @Size(max = 2000)
    private String outcomeNote;
}
