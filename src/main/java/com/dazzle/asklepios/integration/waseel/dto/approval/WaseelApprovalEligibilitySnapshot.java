package com.dazzle.asklepios.integration.waseel.dto.approval;

public record WaseelApprovalEligibilitySnapshot(
        Boolean transfer,
        Boolean isNewBorn,
        WaseelApprovalBeneficiary beneficiary,
        WaseelApprovalInsurancePlan insurancePlan,
        String eligibilityResponseId,
        String eligibilityResponseUrl
) {}