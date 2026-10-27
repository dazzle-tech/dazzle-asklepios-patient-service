package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintPreferredContactMethod;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_complaint")
@Getter
@Setter
public class PatientComplaint extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_reference", nullable = false, length = 40, unique = true)
    private String complaintReference;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_identifier", length = 100)
    private String patientIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50)
    private DocumentType documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 20)
    private PatientComplaintPreferredContactMethod preferredContactMethod;

    @Column(name = "on_behalf")
    private Boolean onBehalf;

    @Column(name = "relationship_to_patient", length = 100)
    private String relationshipToPatient;

    @Column(name = "consent_to_contact", nullable = false)
    private boolean consentToContact;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 40)
    private PatientComplaintFeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "complaint_type", length = 40)
    private PatientComplaintType complaintType;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(name = "incident_time")
    private LocalTime incidentTime;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "involved_staff_names", length = 500)
    private String involvedStaffNames;

    @Column(name = "desired_resolution", columnDefinition = "text")
    private String desiredResolution;

    @Column(name = "received_date", nullable = false)
    private Instant receivedDate;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    @Column(name = "target_deadline")
    private Instant targetDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PatientComplaintStatus status;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Column(name = "reopen_reason", columnDefinition = "text")
    private String reopenReason;

    @OneToMany(
        mappedBy = "complaint",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("id ASC")
    private List<PatientComplaintAction> actions = new ArrayList<>();

    @OneToMany(
        mappedBy = "complaint",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("id ASC")
    private List<PatientComplaintAttachment> attachments = new ArrayList<>();

    @Override
    public Long getId() {
        return id;
    }

    public void addAction(PatientComplaintAction action) {
        actions.add(action);
        action.setComplaint(this);
    }

    public void addAttachment(PatientComplaintAttachment attachment) {
        attachments.add(attachment);
        attachment.setComplaint(this);
    }
}
