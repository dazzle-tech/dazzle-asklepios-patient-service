package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PatientAdministrativeWarningsRepository extends JpaRepository<PatientAdministrativeWarnings, Long> {

    @EntityGraph(attributePaths = "patient")
    List<PatientAdministrativeWarnings> findByPatientIdAndWarningTypeContainsIgnoreCaseOrPatientIdAndDescriptionContainsIgnoreCase(Long patientId, String warningTypeText, Long patientId2, String descriptionText);

    @EntityGraph(attributePaths = "patient")
    List<PatientAdministrativeWarnings> findByPatientId(Long patientId);

    @EntityGraph(attributePaths = "patient")
    List<PatientAdministrativeWarnings> findByResolvedFalse();

    @EntityGraph(attributePaths = "patient")
    List<PatientAdministrativeWarnings> findByWarningTypeInAndResolvedFalse(List<String> warningTypes);
}
