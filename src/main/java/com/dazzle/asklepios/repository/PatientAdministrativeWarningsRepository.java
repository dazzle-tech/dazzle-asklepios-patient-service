package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientAdministrativeWarningsRepository extends JpaRepository<PatientAdministrativeWarnings, Long> {
    List<PatientAdministrativeWarnings> findByPatientIdAndWarningTypeContainsIgnoreCaseOrPatientIdAndDescriptionContainsIgnoreCase(Long patientId, String warningTypeText, Long patientId2, String descriptionText);
    List<PatientAdministrativeWarnings> findByPatientId(Long patientId);
}
