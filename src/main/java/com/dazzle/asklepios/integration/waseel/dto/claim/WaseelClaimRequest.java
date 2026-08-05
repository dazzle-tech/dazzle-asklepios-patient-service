package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalBeneficiary;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCareTeam;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSubscriber;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaseelClaimRequest(
        Boolean transfer,
        Boolean isNewBorn,
        WaseelApprovalBeneficiary beneficiary,
        WaseelApprovalSubscriber subscriber,
        String destinationId,
        WaseelApprovalInsurancePlan insurancePlan,
        WaseelClaimPreAuthorizationInfo preAuthorizationInfo,
        List<WaseelApprovalSupportingInfo> supportingInfo,
        List<WaseelApprovalDiagnosis> diagnosis,
        List<WaseelApprovalCareTeam> careTeam,
        Object accident,
        Object visionPrescription,
        String specialtyReferenceDetails,
        String specialtyReferralOfflineId,
        Object specialtyReferralOfflineDate,
        String referredClinicSpecialty,
        String referralName,
        WaseelClaimEncounter claimEncounter,
        List<WaseelApprovalItem> items,
        BigDecimal totalNet,
        Long uploadId,
        String patientFileNumber,
        Long claimId,
        String provClaimNo,
        List<String> preAuthRefNo
) {}
