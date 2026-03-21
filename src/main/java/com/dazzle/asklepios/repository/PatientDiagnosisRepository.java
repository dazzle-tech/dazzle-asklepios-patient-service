package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientDiagnosisRepository extends JpaRepository<PatientDiagnosis, Long> {

    Optional<PatientDiagnosis> findTopByEncounterIdOrderByCreatedDateDesc(Long encounterId);

    Page<PatientDiagnosis> findByPatient_IdOrderByCreatedDateDesc(
            Long patientId,
            Pageable pageable
    );
    boolean existsByEncounterId(Long encounterId);

    List<PatientDiagnosis> findByEncounterId(Long encounterId);

    Optional<PatientDiagnosis> findByEncounterIdAndType(Long encounterId, DiagnosisType type);
}