package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalEncounterMapper;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
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
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientRelationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.BillingEngineService;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEncounter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
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
    private final BillingEngineService billingEngineService;

    public ApprovalRequestBuilderService(
            ApprovalEligibilitySnapshotService snapshotService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            PatientEncounterRepository encounterRepository,
            PatientDiagnosisRepository patientDiagnosisRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PatientRelationRepository patientRelationRepository,
            PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository,
            PatientPrescriptionRepository patientPrescriptionRepository,
            ApprovalPreAuthorizationInfoMapper preAuthorizationInfoMapper,
            ApprovalEncounterMapper encounterMapper,
            ApprovalDiagnosisMapper approvalDiagnosisMapper,
            ApprovalCareTeamMapper approvalCareTeamMapper,
            ApprovalItemMapper approvalItemMapper,
            ApprovalSupportingInfoMapper approvalSupportingInfoMapper,
            ApprovalSubscriberMapper approvalSubscriberMapper,
            WaseelApiProperties waseelApiProperties,
            @Lazy BillingEngineService billingEngineService
    ) {
        this.snapshotService = snapshotService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.encounterRepository = encounterRepository;
        this.patientDiagnosisRepository = patientDiagnosisRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.patientRelationRepository = patientRelationRepository;
        this.patientPrescriptionMedicationRepository = patientPrescriptionMedicationRepository;
        this.patientPrescriptionRepository = patientPrescriptionRepository;
        this.preAuthorizationInfoMapper = preAuthorizationInfoMapper;
        this.encounterMapper = encounterMapper;
        this.approvalDiagnosisMapper = approvalDiagnosisMapper;
        this.approvalCareTeamMapper = approvalCareTeamMapper;
        this.approvalItemMapper = approvalItemMapper;
        this.approvalSupportingInfoMapper = approvalSupportingInfoMapper;
        this.approvalSubscriberMapper = approvalSubscriberMapper;
        this.waseelApiProperties = waseelApiProperties;
        this.billingEngineService = billingEngineService;
    }

    public WaseelApprovalRequest buildRequest(Long eligibilityRequestId, Long encounterId) {
        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository
                        .findByEncounterIdAndPreAuthorizationStatusAndPreAuthorizationRequestIdIsNull(
                                encounterId,
                                PreAuthorizationStatus.PENDING_APPROVAL
                        );

        return buildRequest(eligibilityRequestId, encounterId, items);
    }

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
        ensurePositiveItemPricing(pendingItems, encounter);

        WaseelApprovalEncounter waseelEncounter =
                encounterMapper.toWaseelEncounter(encounter, nphiesId);

        var supportingInfo = approvalSupportingInfoMapper.toSupportingInfo(encounter);

        var waseelItems = approvalItemMapper.toWaseelItems(
                pendingItems,
                snapshot.insurancePlan() == null ? null : snapshot.insurancePlan().patientShare(),
                encounter,
                supportingInfo
        );

        BigDecimal totalNet = calculateTotalNet(waseelItems);
        if (totalNet.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Pre-authorization total must be a positive number greater than zero "
                            + "(NPHIES BV-01169). Please set a price on the billed items, then resubmit.",
                    "preAuthorization",
                    "total.mustBePositive"
            );
        }

        return new WaseelApprovalRequest(
                Boolean.TRUE.equals(snapshot.transfer()),
                Boolean.TRUE.equals(snapshot.isNewBorn()),
                snapshot.beneficiary(),
                subscriber,
                null,
                snapshot.insurancePlan(),
                preAuthorizationInfoMapper.toPreAuthorizationInfo(
                        snapshot,
                        nphiesId,
                        resolvePrescriptionReference(pendingItems)
                ),
                supportingInfo,
                approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses),
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

    /**
     * NPHIES BV-01169 requires Claim/PreAuthorization total &gt; 0.
     * Re-resolve insurance/setup pricing when the billing row still has a zero price
     * (common when pre-auth is submitted before charge-line billing).
     */
    private void ensurePositiveItemPricing(
            List<PatientServiceAndProduct> items,
            PatientEncounter encounter
    ) {
        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to resolve pre-authorization pricing.",
                    "preAuthorization",
                    "encounter.facility.required"
            );
        }

        boolean updated = false;
        for (PatientServiceAndProduct item : items) {
            if (hasPositivePrice(item)) {
                continue;
            }

            applyResolvedPricing(item, facilityId);
            updated = true;

            if (!hasPositivePrice(item)) {
                throw new BadRequestAlertException(
                        "Item '"
                                + resolveItemLabel(item)
                                + "' has no positive price. In Claim and PreAuthorization, "
                                + "total shall be a positive number greater than zero (BV-01169).",
                        "preAuthorization",
                        "item.unitPrice.mustBePositive"
                );
            }
        }

        if (updated) {
            patientServiceAndProductRepository.saveAll(items);
            patientServiceAndProductRepository.flush();
        }
    }

    private void applyResolvedPricing(PatientServiceAndProduct item, Long facilityId) {
        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(item, facilityId);

        BigDecimal unitPrice = money(resolvedPrice.unitPrice());
        if (unitPrice.signum() <= 0) {
            return;
        }

        long quantity = item.getQuantity() == null || item.getQuantity() <= 0
                ? 1L
                : item.getQuantity();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        item.setUnitPrice(unitPrice);
        if (resolvedPrice.currency() != null) {
            item.setCurrency(resolvedPrice.currency());
        }
        item.setTotalAmount(totalAmount);
        item.setGrossAmount(totalAmount);
        item.setNetAmount(totalAmount);
        item.setRemainingAmount(totalAmount);
        item.setPriceSource(mapPriceSource(resolvedPrice.priceSource()));
    }

    private boolean hasPositivePrice(PatientServiceAndProduct item) {
        return money(item.getUnitPrice()).signum() > 0
                || money(item.getNetAmount()).signum() > 0
                || money(item.getTotalAmount()).signum() > 0
                || money(item.getGrossAmount()).signum() > 0;
    }

    private String resolveItemLabel(PatientServiceAndProduct item) {
        if (item.getWaseelSbsCode() != null && !item.getWaseelSbsCode().isBlank()) {
            return item.getWaseelSbsCode();
        }
        if (item.getProcedureId() != null) {
            return "procedure " + item.getProcedureId();
        }
        if (item.getServiceId() != null) {
            return "service " + item.getServiceId();
        }
        if (item.getDiagnosticTestId() != null) {
            return "diagnostic test " + item.getDiagnosticTestId();
        }
        if (item.getBrandMedicationId() != null) {
            return "medication " + item.getBrandMedicationId();
        }
        return "item " + item.getId();
    }

    private PriceSource mapPriceSource(BillingPriceSource source) {
        if (source == BillingPriceSource.PRICE_LIST) {
            return PriceSource.PRICE_LIST;
        }
        return PriceSource.DEFAULT;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
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