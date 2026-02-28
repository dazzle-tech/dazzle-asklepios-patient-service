package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientWarnings;
import com.dazzle.asklepios.domain.enumeration.PatientWarningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientWarningsRepository extends JpaRepository<PatientWarnings, Long>, JpaSpecificationExecutor<PatientWarnings> {

    Page<PatientWarnings> findByPatientId(Long patientId, Pageable pageable);
    Page<PatientWarnings> findByPatientIdAndStatusNot(Long patientId, PatientWarningStatus status, Pageable pageable);}