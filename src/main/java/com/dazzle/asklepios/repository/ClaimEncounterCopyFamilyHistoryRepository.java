package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyFamilyHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyFamilyHistoryRepository
        extends JpaRepository<ClaimEncounterCopyFamilyHistory, Long> {

    List<ClaimEncounterCopyFamilyHistory>
    findAllByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterCopyFamilyHistory>
    findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}