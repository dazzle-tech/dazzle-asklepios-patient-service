package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyDiagnosticOrderTestReportRepository
        extends JpaRepository<ClaimEncounterCopyDiagnosticOrderTestReport, Long> {

    List<ClaimEncounterCopyDiagnosticOrderTestReport>
    findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopyDiagnosticOrderTestReport>
    findByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );
}
