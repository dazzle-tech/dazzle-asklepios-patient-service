package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientEncounterFieldAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientEncounterFieldAuditRepository
        extends JpaRepository<PatientEncounterFieldAudit, Long> {

    List<PatientEncounterFieldAudit> findByPatientEncounterIdOrderByLogDateDesc(
            Long patientEncounterId
    );

    List<PatientEncounterFieldAudit> findByPatientEncounterIdAndFieldNameOrderByLogDateDesc(
            Long patientEncounterId,
            String fieldName
    );
}