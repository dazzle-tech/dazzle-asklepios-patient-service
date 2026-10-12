package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyHospitalization;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyHospitalizationRepository
        extends JpaRepository<ClaimEncounterCopyHospitalization, Long> {

    List<ClaimEncounterCopyHospitalization>
    findAllByClaimEncounterCopyId(Long claimEncounterCopyId);

    List<ClaimEncounterCopyHospitalization>
    findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}