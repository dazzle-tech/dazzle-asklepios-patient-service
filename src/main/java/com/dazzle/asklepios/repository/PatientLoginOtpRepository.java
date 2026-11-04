package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientLoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientLoginOtpRepository extends JpaRepository<PatientLoginOtp, Long> {

    List<Patient> findDistinctPatientByPatientIsNotNull();

    Optional<PatientLoginOtp> findTopByPatientIdAndVerifiedAtIsNullOrderByCreatedAtDesc(
            Long patientId
    );

    void deleteByPatientId(Long patientId);
}
