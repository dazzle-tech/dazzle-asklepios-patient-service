package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientLoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientLoginOtpRepository extends JpaRepository<PatientLoginOtp, Long> {

    Optional<PatientLoginOtp> findTopByPatientIdAndVerifiedAtIsNullOrderByCreatedAtDesc(
            Long patientId
    );

    void deleteByPatientId(Long patientId);
}
