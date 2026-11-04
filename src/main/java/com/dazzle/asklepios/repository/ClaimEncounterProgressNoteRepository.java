package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterProgressNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterProgressNoteRepository
        extends JpaRepository<ClaimEncounterProgressNote, Long> {

    List<ClaimEncounterProgressNote> findByClaimEncounterCopyId(
            Long claimEncounterCopyId
    );

    List<ClaimEncounterProgressNote> findByEncounterIdAndCancelledDateIsNull(
            Long encounterId
    );
    List<ClaimEncounterProgressNote> findByEncounterId(Long encounterId);

    List<ClaimEncounterProgressNote> findByClaimEncounterCopyIdAndCancelledDateIsNull(
            Long claimEncounterCopyId
    );
}