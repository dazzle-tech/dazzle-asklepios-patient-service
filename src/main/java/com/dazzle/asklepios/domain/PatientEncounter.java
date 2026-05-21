package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DischargeType;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "patient_encounters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientEncounter extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "encounters"})
    private Patient patient;

    @Column(name = "encounter_number", nullable = false, unique = true, length = 50, updatable = false, insertable = false)
    private String encounterNumber;

    @NotNull
    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @NotNull
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private AppointmentFromTemplate appointment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", nullable = false, length = 50)
    private EncounterType encounterType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_reason", nullable = false, length = 50)
    private EncounterReason encounterReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_encounter_id")
    @JsonIgnoreProperties({"followUpEncounter", "patient", "appointment", "hibernateLazyInitializer", "handler"})
    private PatientEncounter followUpEncounter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private EncounterPriority priorityLevel;

    @Column(name = "origin_type", length = 255)
    private String originType;

    @Column(name = "origin_name", length = 255)
    private String originName;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "department_daily_sequence_number", insertable = false, updatable = false)
    private Integer departmentDailySequenceNumber;

    @Column(name = "encounter_date", updatable = false)
    private LocalDate encounterDate;
    @NotNull
    @Column(name = "encounter_time", nullable = false)
    private LocalTime encounterTime;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EncounterStatus status;

    @Column(name = "chief_complaint", columnDefinition = "text")
    private String chiefComplaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "discharge_type", length = 50)
    private DischargeType dischargeType;

    @Column(name = "discharge_at")
    private LocalDateTime dischargeAt;

    @Column(name = "started_by", length = 50)
    private String startedBy;

    @Column(name = "started_date")
    private Instant startedDate;

    @Column(name = "physical_examination_summery")
    private String physicalExaminationSummery;
}
