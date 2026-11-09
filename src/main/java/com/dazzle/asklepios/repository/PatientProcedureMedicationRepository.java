package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedureMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProcedureMedicationRepository extends JpaRepository<PatientProcedureMedication, Long> {

    List<PatientProcedureMedication> findByPatientProcedure_IdOrderByIdAsc(Long patientProcedureId);

    Optional<PatientProcedureMedication> findByIdAndPatientProcedure_Id(Long id, Long patientProcedureId);
}
