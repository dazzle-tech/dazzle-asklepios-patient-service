package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyHistoryRepository extends JpaRepository<FamilyHistory, Long> {

    Page<FamilyHistory> findAllByPatientId(
            Long patientId,
            Pageable pageable
    );

    Page<FamilyHistory> findAllByPatientIdAndStatus(
            Long patientId,
            PatientHistoryStatus status,
            Pageable pageable
    );
}