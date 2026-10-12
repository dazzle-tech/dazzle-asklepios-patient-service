package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopySurgicalHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopySurgicalHistoryRepository
        extends JpaRepository<ClaimEncounterCopySurgicalHistory, Long> {

    List<ClaimEncounterCopySurgicalHistory> findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopySurgicalHistory> findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}