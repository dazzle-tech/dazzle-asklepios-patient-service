package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.math.BigDecimal;
import java.util.List;

public record WaseelApprovalRequest(
        Boolean transfer,
        Boolean isNewBorn,
        WaseelApprovalBeneficiary beneficiary,
        Object subscriber,
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