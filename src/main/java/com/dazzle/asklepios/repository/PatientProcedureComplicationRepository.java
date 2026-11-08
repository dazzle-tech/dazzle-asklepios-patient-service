package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedureComplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProcedureComplicationRepository extends JpaRepository<PatientProcedureComplication, Long> {

    List<PatientProcedureComplication> findByPatientProcedure_IdOrderByIdAsc(Long patientProcedureId);

    Optional<PatientProcedureComplication> findByIdAndPatientProcedure_Id(Long id, Long patientProcedureId);
}
