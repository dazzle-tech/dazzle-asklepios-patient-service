package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalEncounterMapper;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSubscriber;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.integration.waseel.service.LaboratoryItemSupportingInfoLinker.LabTestSource;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalCareTeamMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalDiagnosisMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalItemMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalPreAuthorizationInfoMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSubscriberMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSupportingInfoMapper;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientRelationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.PatientItemPricingApplicationService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
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
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;

    private final ApprovalPreAuthorizationInfoMapper preAuthorizationInfoMapper;
    private final ApprovalEncounterMapper encounterMapper;
    private final ApprovalDiagnosisMapper approvalDiagnosisMapper;
    private final ApprovalCareTeamMapper approvalCareTeamMapper;
    private final ApprovalItemMapper approvalItemMapper;
    private final ApprovalSupportingInfoMapper approvalSupportingInfoMapper;
    private final ApprovalSubscriberMapper approvalSubscriberMapper;

    private final WaseelApiProperties waseelApiProperties;
    private final PatientItemPricingApplicationService patientItemPricingApplicationService;

    @Transactional(readOnly = true)
    public WaseelApprovalRequest buildRequest(Long eligibilityRequestId, Long encounterId) {
        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository
                        .findByEncounterIdAndPreAuthorizationStatusAndPreAuthorizationRequestIdIsNull(
                                encounterId,
                                PreAuthorizationStatus.PENDING_APPROVAL
                        );

        return buildRequest(eligibilityRequestId, encounterId, items);
    }

    @Transactional(readOnly = true)
    public WaseelApprovalRequest buildRequest(
            Long eligibilityRequestId,
            Long encounterId,
            List<PatientServiceAndProduct> items
    ) {
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

        // Waseel: destinationId is required for a TPA, otherwise it stays null.
        String tpaDestinationId = isBlank(snapshot.insurancePlan().tpaNphiesId()) ? null : destinationId;

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
                    "Diagnosis is required before submitting pre-authorization. "
                            + "Please add a diagnosis for this encounter, then try again.",
                    "preAuthorization",
                    "diagnosis.required"
            );
        }

        List<PatientServiceAndProduct> pendingItems = items == null
                ? List.of()
                : items.stream()
                        .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                        .filter(item -> item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL)
                        .filter(item -> item.getPreAuthorizationRequestId() == null)
                        .toList();

        if (pendingItems.isEmpty()) {
            throw new BadRequestAlertException(
                    "No pending pre-authorization items found",
                    "preAuthorization",
                    "items.notFound"
            );
        }

        validateItems(pendingItems);

        Map<Long, BigDecimal> taxPercentByItem = applyPriceListPricing(pendingItems, encounter);

        WaseelApprovalEncounter waseelEncounter =
                encounterMapper.toWaseelEncounter(encounter, nphiesId);

        List<WaseelApprovalDiagnosis> visitDiagnoses = approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses);
        List<WaseelApprovalDiagnosis> requestDiagnoses = ApprovalDiagnosisMapper.withSinglePrincipal(visitDiagnoses);

        Map<Integer, LabTestSource> labTestSources = new LinkedHashMap<>();
        var supportingInfo = approvalSupportingInfoMapper.toSupportingInfo(encounter, labTestSources);

        var waseelItems = linkItemSupportingInfo(
                pendingItems,
                approvalItemMapper.toWaseelItems(
                        pendingItems,
                        snapshot.insurancePlan() == null ? null : snapshot.insurancePlan().patientShare(),
                        encounter,
                        supportingInfo
                ),
                supportingInfo,
                labTestSources,
                diagnosisSequenceChanges(visitDiagnoses, requestDiagnoses),
                taxPercentByItem
        );

        BigDecimal totalNet = calculateTotalNet(waseelItems);

        return new WaseelApprovalRequest(
                Boolean.TRUE.equals(snapshot.transfer()),
                Boolean.TRUE.equals(snapshot.isNewBorn()),
                snapshot.beneficiary(),
                subscriber,
                tpaDestinationId,
                snapshot.insurancePlan(),
                preAuthorizationInfoMapper.toPreAuthorizationInfo(
                        snapshot,
                        nphiesId,
                        resolvePrescriptionReference(pendingItems)
                ),
                supportingInfo,
                requestDiagnoses,
                approvalCareTeamMapper.toWaseelCareTeam(encounter),
                null,
                null,
                null,
                null,
                waseelEncounter,
                waseelItems,
                totalNet
        );
    }

    private List<WaseelApprovalItem> linkItemSupportingInfo(
            List<PatientServiceAndProduct> products,
            List<WaseelApprovalItem> items,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            Map<Integer, LabTestSource> labTestSources,
            Map<Integer, Integer> diagnosisSequenceChanges,
            Map<Long, BigDecimal> taxPercentByItem
    ) {
        List<Integer> allSequences = supportingInfo == null
                ? List.of()
                : supportingInfo.stream()
                .map(WaseelApprovalSupportingInfo::sequence)
                .filter(Objects::nonNull)
                .toList();
        Set<Integer> consumedLabTestSequences = new LinkedHashSet<>();
        List<WaseelApprovalItem> linked = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            WaseelApprovalItem item = items.get(index);
            PatientServiceAndProduct product = index < products.size() ? products.get(index) : null;
            boolean laboratoryItem = isLaboratoryItem(item.type(), product);
            LaboratoryItemSupportingInfoLinker.ItemSupportingInfoLink labLink =
                    LaboratoryItemSupportingInfoLinker.sequencesForItem(
                            laboratoryItem,
                            product == null ? null : product.getDiagnosticTestId(),
                            laboratoryOrderTestId(product),
                            supportingInfo,
                            allSequences,
                            labTestSources,
                            consumedLabTestSequences
                    );
            if (laboratoryItem && labLink.labTestsOnClaim() > 1 && !labLink.matchedByIdentity()) {
                log.warn(
                        "[PREAUTH_BUILD] Laboratory item {} code {} had no result match. Another test's lab result was not linked.",
                        item.sequence(),
                        item.itemCode()
                );
            }
            boolean medicationItem = isMedicationItem(item.type(), product);
            MedicationDaysSupplyLinker.ItemDaysSupplyLink daysSupplyLink =
                    MedicationDaysSupplyLinker.sequencesForItem(
                            medicationItem,
                            labLink.sequences(),
                            item.supportingInfoSequence(),
                            supportingInfo
                    );
            if (item.type() != null
                    && "MEDICATION-CODES".equalsIgnoreCase(item.type().trim())
                    && daysSupplyLink.daysSupplySequence() == null) {
                log.warn(
                        "[PREAUTH_BUILD] Medication item {} code {} has no days-supply link.",
                        item.sequence(),
                        item.itemCode()
                );
            }
            linked.add(copyWithLinks(
                    item,
                    daysSupplyLink.sequences(),
                    medicationItem
                            ? item.diagnosisSequence().stream()
                                    .map(sequence -> diagnosisSequenceChanges.getOrDefault(sequence, sequence))
                                    .toList()
                            : item.diagnosisSequence(),
                    product == null
                            ? item.taxPercent()
                            : taxPercentByItem.getOrDefault(product.getId(), item.taxPercent())
            ));
        }
        return linked;
    }

    /**
     * The principal diagnosis moves to sequence 1. A medication keeps pointing at its indication.
     */
    private Map<Integer, Integer> diagnosisSequenceChanges(
            List<WaseelApprovalDiagnosis> visitDiagnoses,
            List<WaseelApprovalDiagnosis> requestDiagnoses
    ) {
        Map<Integer, Integer> changes = new HashMap<>();
        Set<Integer> usedSequences = new LinkedHashSet<>();
        for (WaseelApprovalDiagnosis visitDiagnosis : visitDiagnoses) {
            requestDiagnoses.stream()
                    .filter(diagnosis -> Objects.equals(diagnosis.diagnosisCode(), visitDiagnosis.diagnosisCode()))
                    .filter(diagnosis -> !usedSequences.contains(diagnosis.sequence()))
                    .findFirst()
                    .ifPresent(diagnosis -> {
                        usedSequences.add(diagnosis.sequence());
                        changes.put(visitDiagnosis.sequence(), diagnosis.sequence());
                    });
        }
        return changes;
    }

    private WaseelApprovalItem copyWithLinks(
            WaseelApprovalItem item,
            List<Integer> supportingInfoSequences,
            List<Integer> diagnosisSequences,
            BigDecimal taxPercent
    ) {
        return new WaseelApprovalItem(
                item.sequence(),
                item.type(),
                item.itemCode(),
                item.itemDescription(),
                item.nonStandardCode(),
                item.nonStandardDesc(),
                item.isPackage(),
                item.isMaternity(),
                item.bodySite(),
                item.subSite(),
                item.quantity(),
                item.quantityCode(),
                item.unitPrice(),
                item.discount(),
                item.factor(),
                taxPercent,
                item.patientSharePercent(),
                item.net(),
                item.tax(),
                item.patientShare(),
                item.payerShare(),
                item.startDate(),
                item.endDate(),
                supportingInfoSequences,
                item.careTeamSequence(),
                diagnosisSequences,
                item.invoiceNo(),
                item.itemDetails()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isLaboratoryItem(String waseelType, PatientServiceAndProduct product) {
        if (waseelType != null && "LABORATORY".equalsIgnoreCase(waseelType.trim())) {
            return true;
        }
        if (product == null || product.getBillingItemType() == null) {
            return false;
        }
        BillingItemTypes billingType = product.getBillingItemType();
        return billingType == BillingItemTypes.LABORATORY || billingType == BillingItemTypes.PATHOLOGY;
    }

    private boolean isMedicationItem(String waseelType, PatientServiceAndProduct product) {
        if (waseelType != null && "MEDICATION-CODES".equalsIgnoreCase(waseelType.trim())) {
            return true;
        }
        return product != null && product.getBillingItemType() == BillingItemTypes.MEDICATION;
    }

    private Long laboratoryOrderTestId(PatientServiceAndProduct product) {
        if (product == null || product.getServiceSource() != ServiceSource.LABORATORY) {
            return null;
        }
        return product.getSourceId();
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

    /**
     * A held line has no charge line yet, so its tax rate comes from this pricing.
     */
    private Map<Long, BigDecimal> applyPriceListPricing(
            List<PatientServiceAndProduct> items,
            PatientEncounter encounter
    ) {
        if (items == null || items.isEmpty() || encounter == null) {
            return Map.of();
        }

        Long facilityId = encounter.getFacilityId();
        Map<Long, BigDecimal> taxPercentByItem = new HashMap<>();
        for (PatientServiceAndProduct item : items) {
            BigDecimal taxPercent = patientItemPricingApplicationService.applyToItem(item, facilityId);
            taxPercentByItem.put(item.getId(), taxPercent.setScale(2, RoundingMode.HALF_UP));
        }

        patientServiceAndProductRepository.saveAll(items);
        return taxPercentByItem;
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

    private BigDecimal calculateTotalNet(List<WaseelApprovalItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return items.stream()
                .map(WaseelApprovalItem::net)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String resolvePrescriptionReference(List<PatientServiceAndProduct> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        for (PatientServiceAndProduct item : items) {
            if (item.getBillingItemType() != BillingItemTypes.MEDICATION) {
                continue;
            }

            if (item.getServiceSource() != ServiceSource.PRESCRIPTION
                    || item.getSourceId() == null) {
                continue;
            }

            PatientPrescriptionMedication prescriptionMedication =
                    patientPrescriptionMedicationRepository
                            .findById(item.getSourceId())
                            .orElse(null);

            if (prescriptionMedication == null
                    || prescriptionMedication.getPrescriptionHeader() == null) {
                continue;
            }

            PatientPrescription prescription =
                    patientPrescriptionRepository.findById(
                            prescriptionMedication.getPrescriptionHeader().getId()
                    ).orElse(null);

            if (prescription == null) {
                continue;
            }

            if (prescription.getPrescriptionNum() != null) {
                return String.valueOf(prescription.getPrescriptionNum());
            }

            return String.valueOf(prescription.getId());
        }

        return null;
    }
}