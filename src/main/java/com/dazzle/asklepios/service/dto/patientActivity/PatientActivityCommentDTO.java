package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientActivityCommentDTO {

    @NotBlank
    @Size(max = 2000)
    private String body;
}
