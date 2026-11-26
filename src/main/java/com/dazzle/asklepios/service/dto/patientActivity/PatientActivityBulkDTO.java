package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * One change applied to several open tasks. Only the fields that are set are changed;
 * {@code unassign} clears the assignee instead of setting one.
 */
@Getter
@Setter
public class PatientActivityBulkDTO {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;

    @Size(max = 50)
    private String assignedTo;

    private boolean unassign;

    private PatientActivityPriority priority;
}
