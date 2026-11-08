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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_procedure_service_equipment")
@Getter
@Setter
@NoArgsConstructor
public class PatientProcedureServiceEquipment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_procedure_id", nullable = false)
    @JsonIgnore
    private PatientProcedure patientProcedure;

    /** The line in patient_services_and_products that was added from this procedure. */
    @Column(name = "service_product_id", nullable = false)
    private Long serviceProductId;

    @JsonProperty("patientProcedureId")
    public Long getPatientProcedureIdJson() {
        return patientProcedure == null ? null : patientProcedure.getId();
    }
}
