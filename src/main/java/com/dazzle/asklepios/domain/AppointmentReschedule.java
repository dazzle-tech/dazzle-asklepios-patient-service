package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "appointment_reschedule")
@Getter
@Setter
public class AppointmentReschedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "old_appointment_id", nullable = false)
    private Long oldAppointmentId;

    @Column(name = "new_appointment_id", nullable = false)
    private Long newAppointmentId;

    @Column(name = "reschedule_reason", nullable = false)
    private String rescheduleReason;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate = Instant.now();

    @Column(name = "created_by", nullable = false)
    private String createdBy;
}