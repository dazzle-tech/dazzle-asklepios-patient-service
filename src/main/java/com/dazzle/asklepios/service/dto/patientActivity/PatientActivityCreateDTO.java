package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PatientActivityCreateDTO {

    @NotNull
    private Long patientId;

    private Long encounterId;

    private Long appointmentId;

    /** Ignored when present: the category always comes from the template. */
    private String category;

    @NotBlank
    @Size(max = 80)
    private String activityType;

    /** Defaults from the template kind when empty. */
    private PatientActivityStatus status;

    private PatientActivityPriority priority;

    @Size(max = 2000)
    private String note;

    /** Defaults from the template due rule when empty. */
    private Instant dueAt;

    /** Defaults from the template assignee roles when null. */
    private List<String> assignedRoles;

    @Size(max = 50)
    private String assignedTo;

    /** Outcome of an activity logged as done. */
    @Size(max = 100)
    private String outcome;
}
