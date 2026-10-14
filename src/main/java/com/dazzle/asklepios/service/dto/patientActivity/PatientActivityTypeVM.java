package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PatientActivityTypeVM {

    private final String value;
    private final PatientActivityCategory category;
    private final PatientActivityStatus defaultStatus;
    private final PatientActivityPriority defaultPriority;
}
