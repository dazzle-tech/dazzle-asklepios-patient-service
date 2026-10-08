package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyDiagnosticOrderTestResultRepository
        extends JpaRepository<ClaimEncounterCopyDiagnosticOrderTestResult, Long> {

    List<ClaimEncounterCopyDiagnosticOrderTestResult>
    findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopyDiagnosticOrderTestResult>
    findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}
