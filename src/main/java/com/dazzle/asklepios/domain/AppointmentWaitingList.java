package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.WaitingListPriority;
import com.dazzle.asklepios.domain.enumeration.WaitingListStatus;
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
@Table(name = "appointment_waiting_list")
@Getter
@Setter
public class AppointmentWaitingList extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private WaitingListPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WaitingListStatus status;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_group_id")
    private AppointmentBookingGroup bookingGroup;

    @Column(name = "booked_at")
    private Instant bookedAt;
}