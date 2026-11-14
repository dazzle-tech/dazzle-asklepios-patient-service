package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalPreAuthorizationInfo;
import com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationClaimType;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApprovalPreAuthorizationInfoMapper {

    public WaseelApprovalPreAuthorizationInfo toPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            String providerNphiesId
    ) {
        return toPreAuthorizationInfo(snapshot, providerNphiesId, null);
    }

    public WaseelApprovalPreAuthorizationInfo toPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            String providerNphiesId,
            String prescriptionReference
    ) {
        return toPreAuthorizationInfo(snapshot, providerNphiesId, prescriptionReference, PreAuthorizationClaimType.PROFESSIONAL_OP);
    }

    /** {@code type} / {@code subType} follow the claim type, e.g. "oral" / "op" for dental treatment. */
    public WaseelApprovalPreAuthorizationInfo toPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            String providerNphiesId,
            String prescriptionReference,
            PreAuthorizationClaimType claimType
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
                claimType.nphiesType(),
                claimType.nphiesSubType(),
                emptyToNull(prescriptionReference),
                null,
                null,
                snapshot.eligibilityResponseId(),
                snapshot.eligibilityResponseUrl(),
                null
        );
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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