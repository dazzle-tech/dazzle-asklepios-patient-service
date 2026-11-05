package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedurePerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProcedurePerformanceRepository extends JpaRepository<PatientProcedurePerformance, Long> {

    Optional<PatientProcedurePerformance> findByPatientProcedure_Id(Long patientProcedureId);
}
