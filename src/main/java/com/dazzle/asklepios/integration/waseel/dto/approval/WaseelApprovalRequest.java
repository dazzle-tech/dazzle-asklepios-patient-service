package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelApprovalRequest(
        Boolean transfer,
        Boolean isNewBorn,
        WaseelApprovalBeneficiary beneficiary,
        WaseelApprovalSubscriber subscriber,
        String destinationId,
        WaseelApprovalInsurancePlan insurancePlan,
        WaseelApprovalPreAuthorizationInfo preAuthorizationInfo,
        List<WaseelApprovalSupportingInfo> supportingInfo,
        List<WaseelApprovalDiagnosis> diagnosis,
        List<WaseelApprovalCareTeam> careTeam,
        String specialtyReferenceDetails,
        String specialtyReferralOfflineId,
        Object specialtyReferralOfflineDate,
        String referredClinicSpecialty,
        WaseelApprovalEncounter encounter,
        List<WaseelApprovalItem> items,
        BigDecimal totalNet
) {}