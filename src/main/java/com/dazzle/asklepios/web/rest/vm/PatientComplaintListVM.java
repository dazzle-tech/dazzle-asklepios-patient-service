package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintType;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintListVM {

    private Long id;
    private String reference;
    private boolean anonymous;
    private String patientName;
    private String subject;
    private PatientComplaintFeedbackType feedbackType;
    private PatientComplaintType complaintType;
    private LocalDate incidentDate;
    private Long facilityId;
    private String facilityName;
    private Long departmentId;
    private String departmentName;
    private Long assignedUserId;
    private String assignedUserName;
    private PatientComplaintStatus status;
    private Instant targetDeadline;
    private Instant receivedDate;
    private boolean overdue;
}
