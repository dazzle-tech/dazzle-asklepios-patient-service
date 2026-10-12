
package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterDiagnosisLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimEncounterDiagnosisLogRepository
        extends JpaRepository<ClaimEncounterDiagnosisLog, Long> {

    void deleteByClaimEncounterDiagnosisId(Long claimEncounterDiagnosisId);
}
