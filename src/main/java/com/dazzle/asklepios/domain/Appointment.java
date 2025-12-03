package com.dazzle.asklepios.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Entity for Appointment table.
 */
@Entity
@Table(name = "ap_appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Appointment implements Serializable {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "patient_key")
    private String patientKey;

    @Column(name = "facility_key")
    private String facilityKey;

    @Column(name = "resource_type_lkey")
    private String resourceTypeLkey;

    @Column(name = "resource_key")
    private String resourceKey;

    @Column(name = "visit_type_lkey")
    private String visitTypeLkey;

    @Column(name = "duration_lkey")
    private String durationLkey;

    @Column(name = "appointment_start")
    private String appointmentStart;

    @Column(name = "appointment_end")
    private String appointmentEnd;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "priority_lkey")
    private String priorityLkey;

    @Column(name = "is_reminder")
    private String isReminder;

    @Column(name = "reminder_lkey")
    private String reminderLkey;

    @Column(name = "consent_form", columnDefinition = "TEXT")
    private String consentForm;

    @Column(name = "refering_physician_lkey")
    private String referingPhysicianLkey;

    @Column(name = "external_physician")
    private String externalPhysician;

    @Column(name = "procedure_level_lkey")
    private String procedureLevelLkey;

    @Column(name = "resource_lkey")
    private String resourceLkey;

    @Column(name = "instructions_lkey")
    private String instructionsLkey;

    @Column(name = "appointment_status")
    private String appointmentStatus;

    @Column(name = "reason_lkey")
    private String reasonLkey;

    @Column(name = "reason_value")
    private String reasonValue;

    @Column(name = "other_reason")
    private String otherReason;

    @Column(name = "no_show_reason_lkey")
    private String noShowReasonLkey;

    @Column(name = "no_show_reason_value")
    private String noShowReasonValue;

    @Column(name = "no_show_other_reason")
    private String noShowOtherReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "created_at")
    private BigDecimal createdAt;

    @Column(name = "updated_at")
    private BigDecimal updatedAt;

    @Column(name = "deleted_at")
    private BigDecimal deletedAt;

    @Column(name = "is_valid")
    private Boolean isValid = true;
}

