package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
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

@Entity
@Table(name = "appointment")
@Getter
@Setter
public class AppointmentFromTemplate extends AbstractAuditingEntity<Long> implements Serializable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facility;

    @Column(name = "department_id", nullable = false)
    private Long department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_generation_batch_id")
    private AvailabilityGenerationBatch availabilityGenerationBatch;

    @Column(name = "capacity_index")
    private Integer capacityIndex;

    @Column(name = "start_datetime", nullable = false)
    private Instant startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private Instant endDatetime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "default_service_id")
    private Long defaultService;

    @Column(name = "default_practitioner_id")
    private Long defaultPractitioner;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "service", length = 50)
    private String service;

    @Column(name = "service_group_id")
    private Long serviceGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", nullable = false, length = 20)
    private BookingMode bookingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AppointmentStatus status;

    @Column(name = "deferred", nullable = false)
    private Boolean deferred = false;

    @Column(name = "deferred_at")
    private Instant deferredAt;

    @Column(name = "no_show_reason", length = 255)
    private String noShowReason;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "priority", nullable = false, length = 50)
    private String priority;

    @Column(name = "note")
    private String note;

}