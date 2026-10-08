package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopySocialHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopySocialHistoryRepository
        extends JpaRepository<ClaimEncounterCopySocialHistory, Long> {

    List<ClaimEncounterCopySocialHistory> findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopySocialHistory> findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}