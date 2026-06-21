package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalEncounterMapper;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSubscriber;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalCareTeamMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalDiagnosisMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalItemMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalPreAuthorizationInfoMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSubscriberMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSupportingInfoMapper;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRelationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEncounter;

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
    private final PatientRelationRepository patientRelationRepository;

    private final ApprovalPreAuthorizationInfoMapper preAuthorizationInfoMapper;
    private final ApprovalEncounterMapper encounterMapper;
    private final ApprovalDiagnosisMapper approvalDiagnosisMapper;
    private final ApprovalCareTeamMapper approvalCareTeamMapper;
    private final ApprovalItemMapper approvalItemMapper;
    private final ApprovalSupportingInfoMapper approvalSupportingInfoMapper;
    private final ApprovalSubscriberMapper approvalSubscriberMapper;

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
        String destinationId = resolveDestinationId(snapshot);

        PatientEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        WaseelApprovalSubscriber subscriber =
                Boolean.TRUE.equals(snapshot.isNewBorn())
                        ? buildSubscriber(encounter)
                        : null;

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

        Long providerNphiesId = Long.valueOf(nphiesId);

        WaseelApprovalEncounter waseelEncounter = new WaseelApprovalEncounter(
                "planned",
                "AMB",
                "acute-care",
                encounter.getEncounterDate() == null ? java.time.LocalDate.now() : encounter.getEncounterDate(),
                "ICSE",
                providerNphiesId,
                providerNphiesId,
                null,
                ""
        );

        var supportingInfo = approvalSupportingInfoMapper.toSupportingInfo(encounter);

        var waseelItems = approvalItemMapper.toWaseelItems(
                items,
                snapshot.insurancePlan() == null ? null : snapshot.insurancePlan().patientShare(),
                encounter,
                supportingInfo
        );

        return new WaseelApprovalRequest(
                Boolean.TRUE.equals(snapshot.transfer()),
                Boolean.TRUE.equals(snapshot.isNewBorn()),
                snapshot.beneficiary(),
                subscriber,
                null,
                snapshot.insurancePlan(),
                preAuthorizationInfoMapper.toPreAuthorizationInfo(snapshot, nphiesId),
                supportingInfo,
                approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses),
                approvalCareTeamMapper.toWaseelCareTeam(encounter),
                null,
                null,
                null,
                null,
                waseelEncounter,
                waseelItems,
                calculateTotalNet(items)
        );
    }

    private WaseelApprovalSubscriber buildSubscriber(PatientEncounter encounter) {
        if (encounter.getPatient() == null || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is required for subscriber",
                    "preAuthorization",
                    "subscriber.patient.required"
            );
        }

        Long patientId = encounter.getPatient().getId();

        PatientRelation relation = patientRelationRepository
                .findFirstByPatientIdAndRelationTypeInOrderByIdAsc(
                        patientId,
                        List.of(RelationType.MOTHER, RelationType.FATHER)
                )
                .orElseThrow(() -> new BadRequestAlertException(
                        "Subscriber is required for newborn pre-authorization",
                        "preAuthorization",
                        "subscriber.required"
                ));

        if (relation.getRelativePatient() == null) {
            throw new BadRequestAlertException(
                    "Subscriber relative patient is required",
                    "preAuthorization",
                    "subscriber.relativePatient.required"
            );
        }

        return approvalSubscriberMapper.toSubscriber(relation.getRelativePatient());
    }

    private String resolveDestinationId(WaseelApprovalEligibilitySnapshot snapshot) {
        if (snapshot.insurancePlan() == null) {
            throw new BadRequestAlertException(
                    "Insurance plan is required to resolve destination ID",
                    "preAuthorization",
                    "insurancePlan.required"
            );
        }

        String tpaNphiesId = snapshot.insurancePlan().tpaNphiesId();
        if (tpaNphiesId != null && !tpaNphiesId.isBlank()) {
            return tpaNphiesId;
        }

        String payerNphiesId = snapshot.insurancePlan().payerNphiesId();
        if (payerNphiesId != null && !payerNphiesId.isBlank()) {
            return payerNphiesId;
        }

        throw new BadRequestAlertException(
                "Destination ID is required. Payer/TPA NPHIES ID is missing.",
                "preAuthorization",
                "destinationId.required"
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