package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.util.List;

public record ClaimBatchSubmitRequest(
        List<Long> financialDocumentIds,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType
) {}
