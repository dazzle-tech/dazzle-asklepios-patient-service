package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedurePreAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProcedurePreAssessmentRepository extends JpaRepository<PatientProcedurePreAssessment, Long> {

    Optional<PatientProcedurePreAssessment> findByPatientProcedure_Id(Long patientProcedureId);
}
