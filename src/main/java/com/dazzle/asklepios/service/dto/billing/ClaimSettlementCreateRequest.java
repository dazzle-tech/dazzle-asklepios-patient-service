package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;

import java.util.List;

public record ClaimSettlementCreateRequest(
        List<Long> financialDocumentIds,
        WaseelClaimType claimType,
        WaseelClaimSubType claimSubType
) {
}
