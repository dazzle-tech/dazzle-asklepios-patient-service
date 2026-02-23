package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPrescriptionChronic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface PatientPrescriptionChronicRepository extends JpaRepository<PatientPrescriptionChronic, Long> {

    Page<PatientPrescriptionChronic> findByPatient_Id(Long patientId, Pageable pageable);

    Optional<PatientPrescriptionChronic>
    findFirstByPatient_IdAndMedicationsIdAndActiveIngredientIdAndStrengthAndIsActiveTrue(
            Long patientId, Long medicationsId, Long activeIngredientId, BigDecimal strength
    );

    Optional<PatientPrescriptionChronic>
    findFirstByPatient_IdAndMedicationsIdAndActiveIngredientIdAndStrengthIsNullAndIsActiveTrue(
            Long patientId, Long medicationsId, Long activeIngredientId
    );
}
