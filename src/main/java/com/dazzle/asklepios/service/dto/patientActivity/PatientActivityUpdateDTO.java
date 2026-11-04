package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PatientActivityUpdateDTO {

    private Long encounterId;

    private Long appointmentId;

    /** Ignored when present: the category always comes from the template. */
    private String category;

    @NotBlank
    @Size(max = 80)
    private String activityType;

    private PatientActivityPriority priority;

    @Size(max = 2000)
    private String note;

    private Instant dueAt;

    private List<String> assignedRoles;

    @Size(max = 50)
    private String assignedTo;
}
