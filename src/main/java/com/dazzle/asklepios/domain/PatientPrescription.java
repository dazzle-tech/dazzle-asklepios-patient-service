package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientPrescription extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "prescription_num", nullable = false, insertable = false, updatable = false)
    private Long prescriptionNum;

    @Column(name = "prescription_date", nullable = false)
    private LocalDate prescriptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", length = 50, nullable = false)
    private PrescriptionUrgencyLevel urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PrescriptionStatus status;

    @Column(name = "from_facility_id", nullable = false)
    private Long fromFacilityId;

    @Column(name = "from_department_id", nullable = false)
    private Long fromDepartmentId;

    @Column(name = "to_facility_id")
    private Long toFacilityId;

    @Column(name = "to_department_id")
    private Long toDepartmentId;

    @OneToMany(mappedBy = "prescriptionHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PatientPrescriptionMedication> medications = new ArrayList<>();
}
