package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedureVitalMonitoring;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProcedureVitalMonitoringRepository extends JpaRepository<PatientProcedureVitalMonitoring, Long> {

    List<PatientProcedureVitalMonitoring> findByPatientProcedure_IdOrderByRecordedAtAscIdAsc(Long patientProcedureId);

    Optional<PatientProcedureVitalMonitoring> findByIdAndPatientProcedure_Id(Long id, Long patientProcedureId);
}
