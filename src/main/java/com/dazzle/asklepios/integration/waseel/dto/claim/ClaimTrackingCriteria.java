package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

public record ClaimTrackingCriteria(
        ClaimStatus status,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType,
        String claimReference,
        String provClaimNo,
        String uploadName,
        String preAuthRefNo,
        String encounterId,
        String patientId
) {
}
