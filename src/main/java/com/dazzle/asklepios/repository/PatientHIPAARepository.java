package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientHIPAA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientHIPAARepository extends JpaRepository<PatientHIPAA, Long> {

    Optional<PatientHIPAA> findByPatientId(Long patientId);
}
