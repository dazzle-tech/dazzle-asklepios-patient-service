package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalPreAuthorizationInfo;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApprovalPreAuthorizationInfoMapper {

    public WaseelApprovalPreAuthorizationInfo toPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            String providerNphiesId
    ) {
        if (snapshot == null) {
            throw new BadRequestAlertException(
                    "Eligibility snapshot is required",
                    "preAuthorization",
                    "eligibility.snapshot.required"
            );
        }

        if (snapshot.eligibilityResponseId() == null || snapshot.eligibilityResponseId().isBlank()) {
            throw new BadRequestAlertException(
                    "Eligibility response ID is required for pre-authorization",
                    "preAuthorization",
                    "eligibility.responseId.required"
            );
        }

        return new WaseelApprovalPreAuthorizationInfo(
                LocalDate.now(),
                parseLong(providerNphiesId),
                "provider",
                "professional",
                "op",
                null,
                null,
                null,
                snapshot.eligibilityResponseId(),
                snapshot.eligibilityResponseUrl(),
                null
        );
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}