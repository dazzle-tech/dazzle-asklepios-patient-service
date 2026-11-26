package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedureStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientProcedureStaffRepository extends JpaRepository<PatientProcedureStaff, Long> {

    List<PatientProcedureStaff> findByPatientProcedure_IdOrderByIdAsc(Long patientProcedureId);

    Optional<PatientProcedureStaff> findByIdAndPatientProcedure_Id(Long id, Long patientProcedureId);
}
