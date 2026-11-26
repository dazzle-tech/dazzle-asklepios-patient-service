package com.dazzle.asklepios.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_procedure_performance")
@Getter
@Setter
@NoArgsConstructor
public class PatientProcedurePerformance extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_procedure_id", nullable = false)
    @JsonIgnore
    private PatientProcedure patientProcedure;

    @Column(name = "actual_start_time")
    private Instant actualStartTime;

    @Column(name = "actual_end_time")
    private Instant actualEndTime;

    @Column(name = "anesthesia_used", nullable = false)
    private Boolean anesthesiaUsed = Boolean.FALSE;

    @Column(name = "anesthesia_type")
    private String anesthesiaType;

    @Column(name = "anesthesia_start_time")
    private Instant anesthesiaStartTime;

    @Column(name = "anesthesia_end_time")
    private Instant anesthesiaEndTime;

    @Column(name = "anesthesia_administered_by_id")
    private Long anesthesiaAdministeredById;

    @Column(name = "time_out", nullable = false)
    private Boolean timeOut = Boolean.FALSE;

    @Column(name = "procedure_outcome")
    private String procedureOutcome;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @Column(name = "additional_notes", columnDefinition = "text")
    private String additionalNotes;

    @Column(name = "home_instruction")
    private String homeInstruction;

    @Column(name = "home_instruction_notes", columnDefinition = "text")
    private String homeInstructionNotes;

    @JsonProperty("patientProcedureId")
    public Long getPatientProcedureIdJson() {
        return patientProcedure == null ? null : patientProcedure.getId();
    }
}
