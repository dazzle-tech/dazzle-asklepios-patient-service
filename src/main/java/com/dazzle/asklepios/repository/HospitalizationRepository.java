package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalizationRepository extends JpaRepository<Hospitalization, Long> {

    Page<Hospitalization> findAllByPatientId(Long patientId, Pageable pageable);

    Page<Hospitalization> findAllByPatientIdAndStatusNot(
            Long patientId,
            PatientHistoryStatus status,
            Pageable pageable
    );
    List<Hospitalization> findAllByPatientIdAndStatusNot(
            Long patientId,
            PatientHistoryStatus status
    );
}