package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyDiagnosticOrderTestResultRepository
        extends JpaRepository<ClaimEncounterCopyDiagnosticOrderTestResult, Long> {

    List<ClaimEncounterCopyDiagnosticOrderTestResult>
    findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopyDiagnosticOrderTestResult>
    findByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );
}
