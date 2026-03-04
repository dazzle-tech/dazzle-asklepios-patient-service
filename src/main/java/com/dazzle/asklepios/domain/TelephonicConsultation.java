package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "telephonic_consultation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelephonicConsultation extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @Column(name = "practitioner_id", nullable = false)
    private Long practitionerId;

    @Column(name = "date_of_call", nullable = false)
    private Instant dateOfCall;

    @Lob
    @NotBlank
    @Column(name = "consultation_content", nullable = false)
    private String consultationContent;

    @Column(name = "approval_number")
    private Long approvalNumber;

    @Lob
    @Column(name = "notes")
    private String notes;

    @Lob
    @Column(name = "extra_documentation")
    private String extraDocumentation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DiagnosticStatus status;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_id")
    private Long cancelledBy;
}
