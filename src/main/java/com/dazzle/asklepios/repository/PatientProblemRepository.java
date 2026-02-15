package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProblem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientProblemRepository extends JpaRepository<PatientProblem, Long> {

    Page<PatientProblem> findAllByPatientId(Long patientId, Pageable pageable);
}
