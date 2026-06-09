package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalEncounterMapper;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalCareTeamMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalDiagnosisMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalItemMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalPreAuthorizationInfoMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSupportingInfoMapper;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalRequestBuilderService {

    private final ApprovalEligibilitySnapshotService snapshotService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final PatientEncounterRepository encounterRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    private final ApprovalPreAuthorizationInfoMapper preAuthorizationInfoMapper;
    private final ApprovalEncounterMapper encounterMapper;
    private final ApprovalDiagnosisMapper approvalDiagnosisMapper;
    private final ApprovalCareTeamMapper approvalCareTeamMapper;
    private final ApprovalItemMapper approvalItemMapper;
    private final ApprovalSupportingInfoMapper approvalSupportingInfoMapper;

    private final WaseelApiProperties waseelApiProperties;

    @Transactional(readOnly = true)
    public WaseelApprovalRequest buildRequest(Long eligibilityRequestId, Long encounterId) {
        encounterInsuranceEligibilityService.getValidatedInsuranceForPreAuthorization(encounterId);

        WaseelApprovalEligibilitySnapshot snapshot =
                snapshotService.buildSnapshot(eligibilityRequestId);

        if (snapshot == null) {
            throw new BadRequestAlertException(
                    "Eligibility snapshot not found",
                    "preAuthorization",
                    "eligibility.snapshotNotFound"
            );
        }

        String nphiesId = resolveNphiesId();

        PatientEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        List<PatientDiagnosis> diagnoses =
                patientDiagnosisRepository.findByEncounterId(encounterId);

        if (diagnoses == null || diagnoses.isEmpty()) {
            throw new BadRequestAlertException(
                    "Diagnosis is required before submitting pre-authorization",
                    "preAuthorization",
                    "diagnosis.required"
            );
        }

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findByEncounterIdAndPreAuthorizationStatus(
                        encounterId,
                        PreAuthorizationStatus.PENDING_APPROVAL
                );

        if (items == null || items.isEmpty()) {
            throw new BadRequestAlertException(
                    "No pending pre-authorization items found",
                    "preAuthorization",
                    "items.notFound"
            );
        }

        validateItems(items);

        return new WaseelApprovalRequest(
                Boolean.TRUE.equals(snapshot.transfer()),
                Boolean.TRUE.equals(snapshot.isNewBorn()),
                snapshot.beneficiary(),
                null,
                null,
                snapshot.insurancePlan(),
                preAuthorizationInfoMapper.toPreAuthorizationInfo(
                        snapshot,
                        nphiesId
                ),
                approvalSupportingInfoMapper.toSupportingInfo(encounter),
                approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses),
                approvalCareTeamMapper.toWaseelCareTeam(encounter),
                null,
                null,
                null,
                null,
                encounterMapper.toWaseelEncounter(
                        encounter,
                        nphiesId
                ),
                approvalItemMapper.toWaseelItems(
                        items,
                        snapshot.insurancePlan() == null ? null : snapshot.insurancePlan().patientShare(),
                        encounter
                ),
                calculateTotalNet(items)
        );
    }

    private String resolveNphiesId() {
        if (waseelApiProperties.nphiesId() != null && !waseelApiProperties.nphiesId().isBlank()) {
            return waseelApiProperties.nphiesId();
        }

        throw new BadRequestAlertException(
                "Waseel NPHIES ID is required. Please configure waseel.api.nphies-id.",
                "preAuthorization",
                "waseel.nphiesId.required"
        );
    }

    private void validateItems(List<PatientServiceAndProduct> items) {
        for (PatientServiceAndProduct item : items) {
            if (item.getTotalAmount() == null) {
                throw new BadRequestAlertException(
                        "Item total amount is required",
                        "preAuthorization",
                        "item.totalAmount.required"
                );
            }

            if (item.getUnitPrice() == null) {
                throw new BadRequestAlertException(
                        "Item unit price is required",
                        "preAuthorization",
                        "item.unitPrice.required"
                );
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestAlertException(
                        "Item quantity is required",
                        "preAuthorization",
                        "item.quantity.required"
                );
            }
        }
    }

    private BigDecimal calculateTotalNet(List<PatientServiceAndProduct> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return items.stream()
                .map(PatientServiceAndProduct::getTotalAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}