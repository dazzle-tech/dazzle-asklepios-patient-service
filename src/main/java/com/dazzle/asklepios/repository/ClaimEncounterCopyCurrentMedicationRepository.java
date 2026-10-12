
package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopyCurrentMedication;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEncounterCopyCurrentMedicationRepository
        extends JpaRepository<ClaimEncounterCopyCurrentMedication, Long> {

    List<ClaimEncounterCopyCurrentMedication>
    findAllByClaimEncounterCopyId(Long claimEncounterCopyId);

    List<ClaimEncounterCopyCurrentMedication>
    findByClaimEncounterCopyIdAndStatusNot(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );

    boolean existsByClaimEncounterCopyIdAndStatus(
            Long claimEncounterCopyId,
            PatientHistoryStatus status
    );
}
