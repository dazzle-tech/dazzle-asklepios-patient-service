package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientPrescriptionMedicationRepository extends JpaRepository<PatientPrescriptionMedication, Long> {

    Page<PatientPrescriptionMedication> findByPrescriptionHeader_Id(Long prescriptionHeaderId, Pageable pageable);
    Optional<PatientPrescriptionMedication> findTopByEncounterIdOrderByCreatedDateDesc(Long encounterId);
}
