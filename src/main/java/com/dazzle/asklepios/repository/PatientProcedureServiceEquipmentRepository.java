package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedureServiceEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProcedureServiceEquipmentRepository extends JpaRepository<PatientProcedureServiceEquipment, Long> {

    List<PatientProcedureServiceEquipment> findByPatientProcedure_IdOrderByIdAsc(Long patientProcedureId);

    Optional<PatientProcedureServiceEquipment> findByIdAndPatientProcedure_Id(Long id, Long patientProcedureId);
}
