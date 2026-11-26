package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.TaskScope;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Query parameters of the task lists; every field is optional except the scope.
 */
@Getter
@Setter
public class PatientTaskFilter {

    private TaskScope scope = TaskScope.MINE;

    private List<PatientActivityStatus> status;

    private String category;

    private List<PatientActivityPriority> priority;

    private String planCode;

    private String assignedTo;

    /** Only tasks nobody has taken yet. */
    private Boolean unassigned;

    /** Only open tasks past their due time. */
    private Boolean overdue;

    private Instant dueFrom;

    private Instant dueTo;

    /** Matches the note, outcome, task type, patient name and medical record number. */
    private String search;
}
