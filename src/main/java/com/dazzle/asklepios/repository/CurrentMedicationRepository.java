package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.CurrentMedication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrentMedicationRepository extends JpaRepository<CurrentMedication, Long> {

    Page<CurrentMedication> findAllByPatientId(Long patientId, Pageable pageable);
    boolean existsByPatientIdAndActiveIngredientId(Long patientId, Long activeIngredientId);

}