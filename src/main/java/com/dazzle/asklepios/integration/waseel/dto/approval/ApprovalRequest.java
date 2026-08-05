package com.dazzle.asklepios.integration.waseel.dto.approval;

import java.math.BigDecimal;
import java.util.List;

public record ApprovalRequest(
        Boolean transfer,
        Boolean isNewBorn,
        ApprovalBeneficiaryDTO beneficiary,
        Object subscriber,
        String destinationId,
        ApprovalInsurancePlanDTO insurancePlan,
        PreAuthorizationInfoDTO preAuthorizationInfo,
        List<SupportingInfoDTO> supportingInfo,
        List<DiagnosisDTO> diagnosis,
        List<CareTeamDTO> careTeam,
        String specialtyReferenceDetails,
        String specialtyReferralOfflineId,
        String specialtyReferralOfflineDate,
        Object referredClinicSpecialty,
        EncounterDTO encounter,
        List<ApprovalItemDTO> items,
        BigDecimal totalNet
) {}