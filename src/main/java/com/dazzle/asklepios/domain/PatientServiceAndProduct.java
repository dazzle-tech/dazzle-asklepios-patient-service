package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientServiceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "patient_services_and_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientServiceAndProduct extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private PatientServiceCategory category;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Long quantity = 1L;

}

