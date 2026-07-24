package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientProblemRepository extends JpaRepository<PatientProblem, Long> {
    List<PatientProblem> findAllByPatientId(Long patientId);
    Page<PatientProblem> findAllByPatientId(Long patientId, Pageable pageable);
    Page<PatientProblem> findAllByPatientIdAndStatus(
            Long patientId,
            PatientHistoryStatus status,
            Pageable pageable
    );
}
