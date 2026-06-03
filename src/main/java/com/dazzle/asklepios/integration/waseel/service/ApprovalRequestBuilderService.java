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
        WaseelApprovalEligibilitySnapshot snapshot =
                snapshotService.buildSnapshot(eligibilityRequestId);

        PatientEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        List<PatientDiagnosis> diagnoses =
                patientDiagnosisRepository.findByEncounterId(encounterId);

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

        return new WaseelApprovalRequest(
                snapshot.transfer(),
                snapshot.isNewBorn(),
                snapshot.beneficiary(),
                null,
                null,
                snapshot.insurancePlan(),
                preAuthorizationInfoMapper.toPreAuthorizationInfo(
                        snapshot,
                        waseelApiProperties.providerId()
                ),
                approvalSupportingInfoMapper.toSupportingInfo(encounter),
                approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses),
                approvalCareTeamMapper.toWaseelCareTeam(encounter),
                "",
                "",
                null,
                null,
                encounterMapper.toWaseelEncounter(
                        encounter,
                        waseelApiProperties.providerId()
                ),
                approvalItemMapper.toWaseelItems(items),
                calculateTotalNet(items)
        );
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