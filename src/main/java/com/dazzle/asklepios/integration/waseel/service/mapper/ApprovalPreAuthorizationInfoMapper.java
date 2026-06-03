package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalPreAuthorizationInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApprovalPreAuthorizationInfoMapper {

    public WaseelApprovalPreAuthorizationInfo toPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            String providerId
    ) {
        return new WaseelApprovalPreAuthorizationInfo(
                LocalDate.now(),
                providerId,
                "provider",
                "professional",
                "op",
                "",
                null,
                "",
                snapshot.eligibilityResponseId(),
                snapshot.eligibilityResponseUrl(),
                null
        );
    }
}