package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientAllergiesRepository extends JpaRepository<PatientAllergies, Long>, JpaSpecificationExecutor<PatientAllergies> {

    Page<PatientAllergies> findByPatientId(Long patientId, Pageable pageable);
    Page<PatientAllergies> findByPatientIdAndStatusNot(Long patientId, PatientAllergyStatus status, Pageable pageable);}
