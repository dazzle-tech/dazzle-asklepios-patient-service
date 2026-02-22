package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ConsultationLevel;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "consultation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultation extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "from_facility_id", nullable = false)
    private Long fromFacilityId;

    @NotNull
    @Column(name = "to_facility_id", nullable = false)
    private Long toFacilityId;

    @NotNull
    @Column(name = "from_department_id", nullable = false)
    private Long fromDepartmentId;

    @Column(name = "to_department_id")
    private Long toDepartmentId;

    @Column(name = "consultation_number", nullable = false, updatable = false, insertable = false)
    private Long consultationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false)
    private DestinationType destinationType;

    @Column(name = "consultation_type", nullable = false, length = 50)
    private String consultationType;

    @Column(name = "consultant_speciality")
    private String consultantSpeciality;

    @Column(name = "consultation_method", nullable = false)
    private String consultationMethod;

    @Column(name = "practitioner_id")
    private Long practitionerId;


    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_level", nullable = false, length = 30)
    private ConsultationLevel consultationLevel;

    @NotBlank
    @Column(name = "consultation_content", nullable = false)
    private String consultationContent;

    @Column(name = "notes")
    private String notes;

    @Column(name = "extra_document")
    private String extraDocument;

    @Column(name = "approval_number")
    private Long approvalNumber;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private ConsultationStatus status;

    @Column(name = "response_date")
    private Instant responseDate;

    @Column(name = "response_by")
    private Long responseBy;

    @Column(name = "response_text")
    private String responseText;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "confirmed_date")
    private Instant confirmedDate;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @Column(name = "submitted_by")
    private Long submittedBy;

}
