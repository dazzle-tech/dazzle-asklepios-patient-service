package com.dazzle.asklepios.integration.waseel.dto.approval;

public record WaseelApprovalEligibilitySnapshot(
        Boolean transfer,
        Boolean isNewBorn,
        WaseelApprovalBeneficiary beneficiary,
        WaseelApprovalInsurancePlan insurancePlan,
        String memberId,
        Long patientInsuranceId,
        Long payorId,
        Long payorPlanId,
        String providerId,
        String destinationId,
        String eligibilityResponseId,
        String eligibilityResponseUrl
) {}