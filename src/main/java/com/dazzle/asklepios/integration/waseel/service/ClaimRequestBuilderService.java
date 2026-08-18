package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalEncounterMapper;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSubscriber;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimEncounter;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimPreAuthorizationInfo;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalCareTeamMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalDiagnosisMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalItemMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSubscriberMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApprovalSupportingInfoMapper;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRelationRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a Waseel claim request by reusing the Pre-Authorization mapping layer
 * and enriching with finalized invoice / billing / approved authorization data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimRequestBuilderService {

    private static final String PRE_AUTH_IDENTIFIER_URL =
            "http://nphies.sa/fhir/ksa/nphies-fs/StructureDefinition/extension-priorauth-response";

    private final ApprovalEligibilitySnapshotService snapshotService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final EligibilityRequestResolverService eligibilityRequestResolverService;

    private final PatientEncounterRepository encounterRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientRelationRepository patientRelationRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingPaymentRepository billingPaymentRepository;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;

    private final ApprovalEncounterMapper encounterMapper;
    private final ApprovalDiagnosisMapper approvalDiagnosisMapper;
    private final ApprovalCareTeamMapper approvalCareTeamMapper;
    private final ApprovalItemMapper approvalItemMapper;
    private final ApprovalSupportingInfoMapper approvalSupportingInfoMapper;
    private final ApprovalSubscriberMapper approvalSubscriberMapper;

    private final WaseelApiProperties waseelApiProperties;

    public record ClaimBuildResult(
            WaseelClaimRequest claimRequest,
            PreAuthorizationRequest primaryPreAuthorization,
            List<FinancialDocumentItem> invoiceItems,
            List<BillingChargeLine> chargeLines,
            List<BillingPayment> payments,
            String uploadName,
            String provClaimNo
    ) {}

    public ClaimBuildResult buildForInsuranceInvoice(FinancialDocument insuranceInvoice) {
        if (insuranceInvoice == null || insuranceInvoice.getId() == null) {
            throw new BadRequestAlertException(
                    "Insurance invoice is required for claim generation",
                    "claim",
                    "invoice.required"
            );
        }

        Long encounterId = insuranceInvoice.getEncounterId();
        encounterInsuranceEligibilityService.getValidatedInsuranceForPreAuthorization(encounterId);

        PatientEncounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "claim",
                        "encounter.notFound"
                ));

        Long eligibilityRequestId =
                eligibilityRequestResolverService.resolveLatestSuccessfulEligibilityId(encounter);

        WaseelApprovalEligibilitySnapshot snapshot =
                snapshotService.buildSnapshot(eligibilityRequestId);

        if (snapshot == null) {
            throw new BadRequestAlertException(
                    "Eligibility snapshot not found",
                    "claim",
                    "eligibility.snapshotNotFound"
            );
        }

        List<FinancialDocumentItem> invoiceItems =
                financialDocumentItemRepository.findByDocument_Id(insuranceInvoice.getId());

        if (invoiceItems == null || invoiceItems.isEmpty()) {
            throw new BadRequestAlertException(
                    "Insurance invoice has no line items for claim generation",
                    "claim",
                    "invoice.items.empty"
            );
        }

        List<Long> chargeLineIds = invoiceItems.stream()
                .map(FinancialDocumentItem::getBillingChargeLineId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<BillingChargeLine> chargeLines = chargeLineIds.isEmpty()
                ? List.of()
                : billingChargeLineRepository.findAllById(chargeLineIds);

        List<BillingPayment> payments =
                billingPaymentRepository.findAllByEncounter_IdOrderByIdAsc(encounterId);

        List<PreAuthorizationRequest> preAuths =
                preAuthorizationRequestRepository.findByEncounterIdOrderByIdDesc(encounterId)
                        .stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getIsCancelled()))
                        .filter(this::isUsablePreAuthorization)
                        .toList();

        PreAuthorizationRequest primaryPreAuth = preAuths.stream()
                .filter(p -> p.getApprovalResponseId() != null)
                .findFirst()
                .orElse(preAuths.isEmpty() ? null : preAuths.get(0));

        String nphiesId = resolveNphiesId();
        String destinationId = resolveDestinationId(snapshot);

        WaseelApprovalSubscriber subscriber =
                Boolean.TRUE.equals(snapshot.isNewBorn())
                        ? buildSubscriber(encounter)
                        : null;

        List<PatientDiagnosis> diagnoses =
                patientDiagnosisRepository.findByEncounterId(encounterId);

        if (diagnoses == null || diagnoses.isEmpty()) {
            throw new BadRequestAlertException(
                    "Diagnosis is required before submitting a claim",
                    "claim",
                    "diagnosis.required"
            );
        }

        var supportingInfo = approvalSupportingInfoMapper.toSupportingInfo(encounter);

        List<PatientServiceAndProduct> products = loadInvoiceProducts(invoiceItems);

        List<WaseelApprovalItem> mappedItems = approvalItemMapper.toWaseelItems(
                products,
                snapshot.insurancePlan() == null ? null : snapshot.insurancePlan().patientShare(),
                encounter,
                supportingInfo,
                true
        );

        Map<Long, PatientServiceAndProduct> productById = products.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(
                        PatientServiceAndProduct::getId,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Long, WaseelApprovalItem> mappedByPspId = alignMappedItemsByProductOrder(
                products,
                mappedItems
        );

        List<Integer> diagnosisSequences = approvalDiagnosisMapper
                .toWaseelDiagnosisList(diagnoses)
                .stream()
                .map(d -> d.sequence())
                .filter(Objects::nonNull)
                .toList();

        List<Integer> careTeamSequences = approvalCareTeamMapper
                .toWaseelCareTeam(encounter)
                .stream()
                .map(c -> c.sequence())
                .filter(Objects::nonNull)
                .toList();

        List<Integer> supportingInfoSequences = supportingInfo == null
                ? List.of()
                : supportingInfo.stream()
                .map(s -> s.sequence())
                .filter(Objects::nonNull)
                .toList();

        List<WaseelApprovalItem> claimItems = new ArrayList<>();
        int sequence = 1;
        for (FinancialDocumentItem invoiceItem : invoiceItems) {
            Long pspId = invoiceItem.getPatientServiceProductId();
            PatientServiceAndProduct invoiceProduct =
                    pspId == null ? null : productById.get(pspId);
            if (invoiceProduct != null && invoiceProduct.isUncoveredCashItem()) {
                continue;
            }

            WaseelApprovalItem base = pspId == null ? null : mappedByPspId.get(pspId);

            if (base == null && pspId != null) {
                PatientServiceAndProduct product = productById.get(pspId);
                if (product != null) {
                    List<WaseelApprovalItem> single = approvalItemMapper.toWaseelItems(
                            List.of(product),
                            snapshot.insurancePlan() == null
                                    ? null
                                    : snapshot.insurancePlan().patientShare(),
                            encounter,
                            supportingInfo,
                            true
                    );
                    base = single.isEmpty() ? null : single.get(0);
                }
            }

            if (base == null) {
                throw new BadRequestAlertException(
                        "Unable to map invoice item to Waseel claim item. patientServiceProductId="
                                + pspId,
                        "claim",
                        "item.mapping.failed"
                );
            }

            BigDecimal payerShare = money(invoiceItem.getInsuranceShareAmount());
            BigDecimal patientShare = money(invoiceItem.getPatientShareAmount());
            BigDecimal net = money(invoiceItem.getNetAmount());
            if (payerShare.signum() > 0 && patientShare.signum() == 0) {
                // Insurance claim invoice lines carry insurance share as net
                patientShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                payerShare = net;
            }

            Integer quantity = invoiceItem.getQuantity() == null
                    ? base.quantity()
                    : invoiceItem.getQuantity().intValue();

            WaseelApprovalItem enriched = approvalItemMapper.withInvoiceAmounts(
                    withSequences(base, sequence++, supportingInfoSequences, careTeamSequences, diagnosisSequences),
                    insuranceInvoice.getDocumentNumber(),
                    invoiceItem.getUnitPrice(),
                    invoiceItem.getGrossAmount(),
                    invoiceItem.getDiscountAmount(),
                    invoiceItem.getTaxAmount(),
                    net,
                    patientShare,
                    payerShare,
                    quantity
            );
            claimItems.add(enriched);
        }

        LocalDate claimDate = encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();
        LocalDate accountingPeriod = resolveAccountingPeriod(claimDate);

        WaseelClaimPreAuthorizationInfo preAuthInfo = buildClaimPreAuthorizationInfo(
                snapshot,
                primaryPreAuth,
                nphiesId,
                encounter,
                claimDate,
                accountingPeriod
        );

        WaseelClaimEncounter claimEncounter = buildClaimEncounter(encounter, nphiesId, claimDate);

        List<String> preAuthRefNos = collectPreAuthRefNos(preAuths, primaryPreAuth);

        String episodeId = preAuthInfo.episodeId();
        String provClaimNo = buildProvClaimNo(episodeId, encounter, insuranceInvoice);
        String uploadName = buildUploadName(encounter, insuranceInvoice);

        String patientFileNumber = resolvePatientFileNumber(encounter, snapshot);

        BigDecimal totalNet = claimItems.stream()
                .map(WaseelApprovalItem::net)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        WaseelClaimRequest claimRequest = new WaseelClaimRequest(
                Boolean.TRUE.equals(snapshot.transfer()) ? Boolean.TRUE : null,
                Boolean.TRUE.equals(snapshot.isNewBorn()),
                snapshot.beneficiary(),
                subscriber,
                destinationId,
                snapshot.insurancePlan(),
                preAuthInfo,
                supportingInfo,
                approvalDiagnosisMapper.toWaseelDiagnosisList(diagnoses),
                approvalCareTeamMapper.toWaseelCareTeam(encounter),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                claimEncounter,
                claimItems,
                totalNet,
                null,
                patientFileNumber,
                null,
                provClaimNo,
                preAuthRefNos.isEmpty() ? null : preAuthRefNos
        );

        log.info(
                "[CLAIM_BUILD] Built claim for encounterId={} invoiceId={} items={} preAuthId={} payments={}",
                encounterId,
                insuranceInvoice.getId(),
                claimItems.size(),
                primaryPreAuth == null ? null : primaryPreAuth.getId(),
                payments.size()
        );

        return new ClaimBuildResult(
                claimRequest,
                primaryPreAuth,
                invoiceItems,
                chargeLines,
                payments,
                uploadName,
                provClaimNo
        );
    }

    private Map<Long, WaseelApprovalItem> alignMappedItemsByProductOrder(
            List<PatientServiceAndProduct> products,
            List<WaseelApprovalItem> mappedItems
    ) {
        Map<Long, WaseelApprovalItem> result = new LinkedHashMap<>();
        int index = 0;
        for (PatientServiceAndProduct product : products) {
            if (product.getId() == null) {
                continue;
            }
            if (index < mappedItems.size()) {
                result.put(product.getId(), mappedItems.get(index));
            }
            index++;
        }
        return result;
    }

    private List<PatientServiceAndProduct> loadInvoiceProducts(List<FinancialDocumentItem> invoiceItems) {
        List<Long> pspIds = invoiceItems.stream()
                .map(FinancialDocumentItem::getPatientServiceProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (pspIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "Invoice items are missing patient service/product references",
                    "claim",
                    "invoice.products.missing"
            );
        }

        Map<Long, PatientServiceAndProduct> byId = patientServiceAndProductRepository
                .findAllById(pspIds)
                .stream()
                .collect(Collectors.toMap(PatientServiceAndProduct::getId, Function.identity()));

        List<PatientServiceAndProduct> ordered = new ArrayList<>();
        for (Long id : pspIds) {
            PatientServiceAndProduct product = byId.get(id);
            if (product == null) {
                throw new BadRequestAlertException(
                        "Patient service/product not found for invoice item: " + id,
                        "claim",
                        "product.notFound"
                );
            }
            ordered.add(product);
        }
        return ordered;
    }

    private WaseelApprovalItem withSequences(
            WaseelApprovalItem item,
            int sequence,
            List<Integer> supportingInfoSequences,
            List<Integer> careTeamSequences,
            List<Integer> diagnosisSequences
    ) {
        return new WaseelApprovalItem(
                sequence,
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
                item.taxPercent(),
                item.patientSharePercent(),
                item.net(),
                item.tax(),
                item.patientShare(),
                item.payerShare(),
                item.startDate(),
                item.endDate(),
                supportingInfoSequences,
                careTeamSequences.isEmpty() ? List.of(1) : careTeamSequences,
                diagnosisSequences.isEmpty() ? List.of(1) : diagnosisSequences,
                item.invoiceNo(),
                item.itemDetails()
        );
    }

    private WaseelClaimPreAuthorizationInfo buildClaimPreAuthorizationInfo(
            WaseelApprovalEligibilitySnapshot snapshot,
            PreAuthorizationRequest preAuth,
            String nphiesId,
            PatientEncounter encounter,
            LocalDate claimDate,
            LocalDate accountingPeriod
    ) {
        String eligibilityResponseId = snapshot.eligibilityResponseId();
        String eligibilityResponseUrl = snapshot.eligibilityResponseUrl();
        String episodeId;
        String type = "professional";
        String subType = "op";
        String payeeType = "provider";
        Long payeeId = parseLong(nphiesId);
        String preAuthResponseId = null;
        LocalDate dateOrdered = claimDate;
        LocalDate eligibilityOfflineDate = null;
        String eligibilityOfflineId = null;

        if (preAuth != null) {
            if (preAuth.getEpisodeId() != null && !preAuth.getEpisodeId().isBlank()) {
                episodeId = preAuth.getEpisodeId();
            } else {
                episodeId = resolveEpisodeId(encounter);
            }
            if (preAuth.getPreauthType() != null) {
                type = preAuth.getPreauthType();
            }
            if (preAuth.getPreauthSubType() != null) {
                subType = preAuth.getPreauthSubType();
            }
            if (preAuth.getPayeeType() != null) {
                payeeType = preAuth.getPayeeType();
            }
            if (preAuth.getPayeeId() != null) {
                payeeId = preAuth.getPayeeId();
            }
            if (preAuth.getApprovalResponseId() != null) {
                preAuthResponseId = String.valueOf(preAuth.getApprovalResponseId());
            }
            if (preAuth.getDateOrdered() != null) {
                dateOrdered = preAuth.getDateOrdered();
            }
            eligibilityOfflineDate = preAuth.getEligibilityOfflineDate();
            eligibilityOfflineId = preAuth.getEligibilityOfflineId();
            if (preAuth.getEligibilityResponseId() != null && !preAuth.getEligibilityResponseId().isBlank()) {
                eligibilityResponseId = preAuth.getEligibilityResponseId();
            }
            if (preAuth.getEligibilityResponseUrl() != null && !preAuth.getEligibilityResponseUrl().isBlank()) {
                eligibilityResponseUrl = preAuth.getEligibilityResponseUrl();
            }
        } else {
            episodeId = resolveEpisodeId(encounter);
        }

        return new WaseelClaimPreAuthorizationInfo(
                dateOrdered,
                type,
                subType,
                payeeId,
                payeeType,
                eligibilityOfflineId,
                eligibilityOfflineDate,
                eligibilityResponseId,
                claimDate,
                claimDate,
                preAuthResponseId,
                preAuthResponseId == null ? null : PRE_AUTH_IDENTIFIER_URL,
                eligibilityResponseUrl,
                null,
                episodeId,
                accountingPeriod
        );
    }

    private LocalDate resolveAccountingPeriod(LocalDate claimDate) {
        LocalDate today = LocalDate.now();
        LocalDate candidate = claimDate == null ? today : claimDate;
        if (!candidate.isBefore(today)) {
            return today.minusDays(1);
        }
        return candidate;
    }

    private WaseelClaimEncounter buildClaimEncounter(
            PatientEncounter encounter,
            String nphiesId,
            LocalDate claimDate
    ) {
        // Reuse approval encounter defaults for provider/serviceEventType, then adapt claim status/class.
        var approvalEncounter = encounterMapper.toWaseelEncounter(encounter, nphiesId);

        return new WaseelClaimEncounter(
                "Finished",
                "AMB",
                approvalEncounter.serviceType() == null ? "" : approvalEncounter.serviceType(),
                claimDate,
                claimDate,
                approvalEncounter.serviceEventType(),
                approvalEncounter.serviceProvider(),
                null,
                ""
        );
    }

    private List<String> collectPreAuthRefNos(
            List<PreAuthorizationRequest> preAuths,
            PreAuthorizationRequest primary
    ) {
        Set<String> refs = new LinkedHashSet<>();
        if (primary != null) {
            addPreAuthRef(refs, primary);
        }
        for (PreAuthorizationRequest preAuth : preAuths) {
            addPreAuthRef(refs, preAuth);
        }
        return new ArrayList<>(refs);
    }

    private void addPreAuthRef(Set<String> refs, PreAuthorizationRequest preAuth) {
        if (preAuth == null) {
            return;
        }
        if (preAuth.getPreAuthRefNo() != null && !preAuth.getPreAuthRefNo().isBlank()) {
            refs.add(preAuth.getPreAuthRefNo().trim());
        } else if (preAuth.getApprovalResponseId() != null) {
            refs.add(String.valueOf(preAuth.getApprovalResponseId()));
        }
    }

    private boolean isUsablePreAuthorization(PreAuthorizationRequest preAuth) {
        if (preAuth == null) {
            return false;
        }
        String status = preAuth.getStatus();
        if (status == null) {
            return preAuth.getApprovalResponseId() != null;
        }
        String normalized = status.trim().toLowerCase();
        return normalized.contains("approv")
                || normalized.contains("partial")
                || normalized.contains("pended")
                || normalized.contains("queued")
                || preAuth.getApprovalResponseId() != null;
    }

    private String buildProvClaimNo(
            String episodeId,
            PatientEncounter encounter,
            FinancialDocument invoice
    ) {
        if (invoice.getClaimReference() != null && !invoice.getClaimReference().isBlank()) {
            return invoice.getClaimReference();
        }
        String episode = episodeId == null || episodeId.isBlank()
                ? resolveEpisodeId(encounter)
                : episodeId;
        return episode
                + "_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_"
                + String.format("%06d", System.currentTimeMillis() % 1_000_000);
    }

    private String buildUploadName(PatientEncounter encounter, FinancialDocument invoice) {
        String encounterNo = encounter.getEncounterNumber() == null
                ? String.valueOf(encounter.getId())
                : encounter.getEncounterNumber();
        return "CLM-" + encounterNo + "-" + invoice.getId() + "-" + System.currentTimeMillis();
    }

    private String resolveEpisodeId(PatientEncounter encounter) {
        if (encounter.getEncounterNumber() != null && !encounter.getEncounterNumber().isBlank()) {
            return encounter.getEncounterNumber();
        }
        return String.valueOf(encounter.getId());
    }

    private String resolvePatientFileNumber(
            PatientEncounter encounter,
            WaseelApprovalEligibilitySnapshot snapshot
    ) {
        if (snapshot != null
                && snapshot.beneficiary() != null
                && snapshot.beneficiary().fileId() != null
                && !snapshot.beneficiary().fileId().isBlank()) {
            return snapshot.beneficiary().fileId();
        }
        Patient patient = encounter.getPatient();
        if (patient != null
                && patient.getMedicalRecordNumber() != null
                && !patient.getMedicalRecordNumber().isBlank()) {
            return patient.getMedicalRecordNumber();
        }
        return encounter.getEncounterNumber();
    }

    private WaseelApprovalSubscriber buildSubscriber(PatientEncounter encounter) {
        if (encounter.getPatient() == null || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is required for subscriber",
                    "claim",
                    "subscriber.patient.required"
            );
        }

        PatientRelation relation = patientRelationRepository
                .findFirstByPatientIdAndRelationTypeInOrderByIdAsc(
                        encounter.getPatient().getId(),
                        List.of(RelationType.MOTHER, RelationType.FATHER)
                )
                .orElseThrow(() -> new BadRequestAlertException(
                        "Subscriber is required for newborn claim",
                        "claim",
                        "subscriber.required"
                ));

        if (relation.getRelativePatient() == null) {
            throw new BadRequestAlertException(
                    "Subscriber relative patient is required",
                    "claim",
                    "subscriber.relativePatient.required"
            );
        }

        return approvalSubscriberMapper.toSubscriber(relation.getRelativePatient());
    }

    private String resolveDestinationId(WaseelApprovalEligibilitySnapshot snapshot) {
        if (snapshot.insurancePlan() == null) {
            throw new BadRequestAlertException(
                    "Insurance plan is required to resolve destination ID",
                    "claim",
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
                "claim",
                "destinationId.required"
        );
    }

    private String resolveNphiesId() {
        if (waseelApiProperties.nphiesId() != null && !waseelApiProperties.nphiesId().isBlank()) {
            return waseelApiProperties.nphiesId();
        }
        throw new BadRequestAlertException(
                "Waseel NPHIES ID is required. Please configure waseel.api.nphies-id.",
                "claim",
                "waseel.nphiesId.required"
        );
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }
}
