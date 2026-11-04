package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.enumeration.PatientComplaintActionStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintActionVM {

    private Long id;
    private String description;
    private Long assignedUserId;
    private String assignedUserName;
    private Instant deadline;
    private PatientComplaintActionStatus status;
    private String result;
    private Long completedByUserId;
    private String completedBy;
    private Instant completedAt;
}
