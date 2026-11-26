package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintPreferredContactMethod;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintDetailVM {

    private Long id;
    private String reference;
    private boolean anonymous;
    private String patientName;
    private String patientIdentifier;
    private DocumentType documentType;
    private String documentNumber;
    private String contactEmail;
    private String contactPhone;
    private PatientComplaintPreferredContactMethod preferredContactMethod;
    private Boolean onBehalf;
    private String relationshipToPatient;
    private boolean consentToContact;
    private Long facilityId;
    private String facilityName;
    private Long departmentId;
    private String departmentName;
    private PatientComplaintFeedbackType feedbackType;
    private PatientComplaintType complaintType;
    private LocalDate incidentDate;
    private LocalTime incidentTime;
    private String subject;
    private String description;
    private String involvedStaffNames;
    private String desiredResolution;
    private Long assignedUserId;
    private String assignedUserName;
    private PatientComplaintStatus status;
    private Instant targetDeadline;
    private Instant receivedDate;
    private String resolutionSummary;
    private String reopenReason;
    private boolean overdue;
    private List<PatientComplaintActionVM> actions = new ArrayList<>();
    private List<PatientComplaintAttachmentVM> attachments = new ArrayList<>();
}
