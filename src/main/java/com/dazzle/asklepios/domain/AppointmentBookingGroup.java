package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AppointmentBookingGroupSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "appointment_booking_group")
@Getter
@Setter
public class AppointmentBookingGroup extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private AppointmentBookingGroupSourceType sourceType;

    @Column(name = "total_duration_minutes", nullable = false)
    private Integer totalDurationMinutes;

    @Column(name = "start_datetime", nullable = false)
    private Instant startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private Instant endDatetime;

    @Column(name = "notes", length = 1000)
    private String notes;


}