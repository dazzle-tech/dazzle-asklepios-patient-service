package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterDiagnosisRepository
        extends JpaRepository<ClaimEncounterDiagnosis, Long> {

    List<ClaimEncounterDiagnosis> findByClaimEncounterCopyId(Long claimEncounterCopyId);

    List<ClaimEncounterDiagnosis> findByEncounterId(Long encounterId);
}