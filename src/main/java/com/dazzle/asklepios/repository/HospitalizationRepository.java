package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Hospitalization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalizationRepository extends JpaRepository<Hospitalization, Long> {

    Page<Hospitalization> findAllByPatientId(Long patientId, Pageable pageable);
}

