package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
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
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "appointment_request")
@Getter
@Setter
public class AppointmentRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_encounter_id", nullable = false)
    private PatientEncounter sourceEncounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private AppointmentFromTemplate appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_resource_type", length = 30)
    private TemplateType requestedResourceType;

    @Column(name = "requested_resource_id")
    private Long requestedResourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private EncounterPriority priority;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppointmentRequestStatus status;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

}