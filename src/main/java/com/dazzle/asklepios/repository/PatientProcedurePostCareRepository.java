package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientProcedurePostCareRepository extends JpaRepository<PatientProcedurePostCare, Long> {

    Optional<PatientProcedurePostCare> findByPatientProcedure_Id(Long patientProcedureId);
}
