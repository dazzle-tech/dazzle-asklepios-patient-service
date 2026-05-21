package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientInsurance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientInsuranceRepository extends JpaRepository<PatientInsurance, Long> {
    Page<PatientInsurance> findByPatientId(Long patientId, Pageable pageable);

    Optional<PatientInsurance> findFirstByPatient_IdAndPayorIdAndPlanIdAndPolicyNumber(
            Long patientId,
            Long payorId,
            Long planId,
            String policyNumber
    );
}
