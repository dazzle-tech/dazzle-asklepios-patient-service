package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Due date and priority of an open task; either may be left out. Works for system tasks too.
 */
@Getter
@Setter
public class PatientActivityScheduleDTO {

    private Instant dueAt;

    private PatientActivityPriority priority;
}
