package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "patient_problems")
@EqualsAndHashCode(callSuper = false)
public class PatientProblem extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn( name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @NotBlank
    @Column(name = "condition", nullable = false)
    private String condition;

    @NotNull
    @Column(name = "date_of_diagnosis", nullable = false)
    private Date dateOfDiagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EncounterVaccinationStatus status;

    @Column(name = "type")
    private String type;

    @Column(name = "date_of_resolution")
    private Date dateOfResolution;

    @NotNull
    @Column(name = "by_patient", nullable = false)
    private Boolean byPatient;

    @Column(name = "source_of_information")
    private String sourceOfInformation;
}
