package com.dazzle.asklepios.service.dto.patientActivity;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PatientActivityAssignDTO {

    private List<String> assignedRoles;

    @Size(max = 50)
    private String assignedTo;
}
