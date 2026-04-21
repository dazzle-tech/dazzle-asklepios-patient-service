package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ToothNumber;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "dental_procedure")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalProcedure extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tooth_number", nullable = false)
    private ToothNumber toothNumber;

    @Column(name = "surface", nullable = false)
    private String surface;

    @Column(name = "anesthesia_used")
    private String anesthesiaUsed;

    @Column(name = "dose", precision = 10, scale = 2)
    private BigDecimal dose;

    @Column(name = "unit")
    private String unit;

    @Column(name = "filling_material")
    private String fillingMaterial;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "cdt_code_id")
    private Long cdtCodeId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;
}