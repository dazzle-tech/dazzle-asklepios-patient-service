package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.time.Instant;

/**
 * Claims registry search. {@code submittedTo} is exclusive.
 */
public record ClaimTrackingCriteria(
        ClaimStatus status,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
        String claimReference,
        String provClaimNo,
        String uploadName,
        String preAuthRefNo,
        String encounterId,
        String patientId,
        String encounterNumber,
        String medicalRecordNumber,
        Instant submittedFrom,
        Instant submittedTo,
        Long payorId,
        String payerNphiesId,
        WaseelClaimStatus waseelStatus,
        boolean latestPerEncounter
) {
}
