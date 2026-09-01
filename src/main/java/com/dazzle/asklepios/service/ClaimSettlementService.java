package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.ClaimItemRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementRowResponse;
import com.dazzle.asklepios.service.helper.NphiesPayerHelper;
import com.dazzle.asklepios.service.helper.PayorHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimSettlementService {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimSettlementService.class);

    private static final String ENTITY_NAME = "claimSettlement";

    private static final int MONEY_SCALE = 2;

    private static final EnumSet<ClaimStatus> REJECTED_STATUSES =
            EnumSet.of(ClaimStatus.REJECTED, ClaimStatus.FAILED);

    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimItemRepository claimItemRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final PayorHelper payorHelper;
    private final NphiesPayerHelper nphiesPayerHelper;

    public Page<ClaimSettlementRowResponse> search(
            String payerNphiesId,
            String encounterTypeValue,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug(
                "[CLAIM_SETTLEMENT] Search payerNphiesId={} encounterType={} from={} to={}",
                payerNphiesId,
                encounterTypeValue,
                fromDate,
                toDate
        );

        EncounterType encounterType = parseEncounterType(encounterTypeValue);
        Long payorId = payorHelper.resolvePayorId(null, payerNphiesId);
        Pageable sorted = withDefaultSort(pageable);

        List<ClaimRequest> claims = claimRequestRepository.findAll(sorted.getSort());
        Map<Long, PatientEncounter> encounters = loadEncounters(claims);
        Map<Long, PatientInsurance> insurances = loadInsurances(claims, encounters);

        List<ClaimRequest> filtered =
                claims.stream()
                        .filter(claim -> claim.getStatus() == ClaimStatus.ACCEPTED)
                        .filter(claim ->
                                matchesPayer(
                                        claim,
                                        encounters,
                                        insurances,
                                        payerNphiesId,
                                        payorId
                                )
                        )
                        .filter(claim ->
                                matchesEncounterType(claim, encounters, encounterType)
                        )
                        .filter(claim ->
                                matchesSettlementDate(claim, fromDate, toDate)
                        )
                        .toList();

        int start = (int) sorted.getOffset();
        int end = Math.min(start + sorted.getPageSize(), filtered.size());
        List<ClaimRequest> pageClaims =
                start >= filtered.size()
                        ? List.of()
                        : filtered.subList(start, end);

        if (pageClaims.isEmpty()) {
            return new PageImpl<>(List.of(), sorted, filtered.size());
        }

        SettlementLookups lookups = loadLookups(pageClaims, encounters, insurances);

        List<ClaimSettlementRowResponse> rows =
                pageClaims.stream()
                        .map(claim -> toRow(claim, lookups))
                        .toList();

        return new PageImpl<>(rows, sorted, filtered.size());
    }

    private Map<Long, PatientEncounter> loadEncounters(List<ClaimRequest> claims) {
        Set<Long> encounterIds =
                claims.stream()
                        .map(ClaimRequest::getEncounterId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (encounterIds.isEmpty()) {
            return Map.of();
        }

        return patientEncounterRepository.findAllById(encounterIds).stream()
                .collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));
    }

    private Map<Long, PatientInsurance> loadInsurances(
            List<ClaimRequest> claims,
            Map<Long, PatientEncounter> encounters
    ) {
        Set<Long> insuranceIds =
                claims.stream()
                        .map(claim -> insuranceIdFor(claim, encounters))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (insuranceIds.isEmpty()) {
            return Map.of();
        }

        return patientInsuranceRepository.findAllById(insuranceIds).stream()
                .collect(Collectors.toMap(PatientInsurance::getId, Function.identity()));
    }

    private boolean matchesPayer(
            ClaimRequest claim,
            Map<Long, PatientEncounter> encounters,
            Map<Long, PatientInsurance> insurances,
            String payerNphiesId,
            Long payorId
    ) {
        if (!hasText(payerNphiesId) && payorId == null) {
            return true;
        }

        PatientInsurance insurance =
                insurances.get(insuranceIdFor(claim, encounters));
        if (insurance == null) {
            return false;
        }

        if (hasText(payerNphiesId)
                && payerNphiesId.equalsIgnoreCase(insurance.getPayerNphiesId())) {
            return true;
        }

        return payorId != null && payorId.equals(insurance.getPayorId());
    }

    private boolean matchesEncounterType(
            ClaimRequest claim,
            Map<Long, PatientEncounter> encounters,
            EncounterType encounterType
    ) {
        if (encounterType == null) {
            return true;
        }

        PatientEncounter encounter = encounters.get(claim.getEncounterId());
        return encounter != null && encounterType == encounter.getEncounterType();
    }

    private boolean matchesSettlementDate(
            ClaimRequest claim,
            Instant fromDate,
            Instant toDate
    ) {
        Instant settlementDate = settlementDate(claim);

        if (fromDate != null
                && (settlementDate == null || settlementDate.isBefore(fromDate))) {
            return false;
        }

        return toDate == null
                || (settlementDate != null && settlementDate.isBefore(toDate));
    }

    private Instant settlementDate(ClaimRequest claim) {
        return claim.getSubmittedAt() != null
                ? claim.getSubmittedAt()
                : claim.getCreatedDate();
    }

    private Long insuranceIdFor(
            ClaimRequest claim,
            Map<Long, PatientEncounter> encounters
    ) {
        if (claim.getPatientInsuranceId() != null) {
            return claim.getPatientInsuranceId();
        }

        PatientEncounter encounter = encounters.get(claim.getEncounterId());
        return encounter == null ? null : encounter.getPatientInsuranceId();
    }

    private SettlementLookups loadLookups(
            List<ClaimRequest> claims,
            Map<Long, PatientEncounter> encounters,
            Map<Long, PatientInsurance> insurances
    ) {
        Set<Long> claimIds =
                claims.stream()
                        .map(ClaimRequest::getId)
                        .collect(Collectors.toSet());

        Map<Long, List<ClaimItem>> itemsByClaim =
                claimItemRepository.findByClaimRequestIdIn(claimIds).stream()
                        .collect(Collectors.groupingBy(ClaimItem::getClaimRequestId));

        Set<Long> chargeLineIds =
                itemsByClaim.values().stream()
                        .flatMap(List::stream)
                        .map(ClaimItem::getBillingChargeLineId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<Long, BillingChargeLine> chargeLines =
                chargeLineIds.isEmpty()
                        ? Map.of()
                        : billingChargeLineRepository.findAllById(chargeLineIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                BillingChargeLine::getId,
                                                Function.identity()
                                        )
                                );

        Set<Long> documentIds =
                claims.stream()
                        .map(ClaimRequest::getFinancialDocumentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<FinancialDocumentItem> documentItems =
                documentIds.isEmpty()
                        ? List.of()
                        : financialDocumentItemRepository.findByDocument_IdIn(documentIds);

        Map<Long, FinancialDocumentItem> documentItemsById =
                documentItems.stream()
                        .collect(
                                Collectors.toMap(
                                        FinancialDocumentItem::getId,
                                        Function.identity()
                                )
                        );

        Map<Long, List<FinancialDocumentItem>> documentItemsByDocument =
                documentItems.stream()
                        .filter(item -> item.getDocument() != null)
                        .collect(
                                Collectors.groupingBy(
                                        item -> item.getDocument().getId()
                                )
                        );

        return new SettlementLookups(
                itemsByClaim,
                encounters,
                insurances,
                chargeLines,
                documentItemsById,
                documentItemsByDocument,
                new HashMap<>()
        );
    }

    private ClaimSettlementRowResponse toRow(
            ClaimRequest claim,
            SettlementLookups lookups
    ) {
        List<ClaimItem> items =
                lookups.itemsByClaim.getOrDefault(claim.getId(), List.of());

        Amounts amounts = amountsFor(claim, items, lookups);
        PatientInsurance insurance =
                lookups.insurances.get(insuranceIdFor(claim, lookups.encounters));

        Instant claimDate = claim.getCreatedDate();
        Instant settlementDate = settlementDate(claim);

        return new ClaimSettlementRowResponse(
                claim.getId(),
                "SET-" + claim.getId(),
                settlementDate,
                resolveInsuranceCompany(insurance, lookups),
                resolveTpa(insurance, lookups),
                firstNonBlank(claim.getProvClaimNo(), claim.getClaimReference()),
                claimDate,
                amounts.billedAmount,
                amounts.approvedAmount,
                amounts.rejectedAmount,
                amounts.patientShare,
                amounts.insuranceAmount,
                amounts.paidAmount,
                amounts.outstandingAmount,
                settlementStatus(claim.getStatus(), amounts)
        );
    }

    private Amounts amountsFor(
            ClaimRequest claim,
            List<ClaimItem> items,
            SettlementLookups lookups
    ) {
        BigDecimal billed = sum(items, ClaimItem::getNet);
        if (billed.signum() == 0) {
            billed = money(claim.getTotalNet());
        }

        BigDecimal patientShare = sum(items, ClaimItem::getPatientShare);
        if (patientShare.signum() == 0) {
            patientShare = patientShareFromChargeLines(items, lookups);
        }
        BigDecimal insuranceAmount = sum(items, ClaimItem::getPayerShare);

        List<FinancialDocumentItem> documentItems =
                documentItemsFor(claim, items, lookups);

        BigDecimal paid = sumDocument(documentItems, FinancialDocumentItem::getInsurancePaidAmount);
        BigDecimal outstanding =
                sumDocument(documentItems, FinancialDocumentItem::getInsuranceRemainingAmount);

        if (outstanding.signum() == 0 && insuranceAmount.signum() > 0) {
            outstanding = money(insuranceAmount.subtract(paid).max(BigDecimal.ZERO));
        }

        boolean rejected = REJECTED_STATUSES.contains(claim.getStatus());
        BigDecimal approved = rejected ? BigDecimal.ZERO.setScale(MONEY_SCALE) : insuranceAmount;
        BigDecimal rejectedAmount =
                rejected
                        ? (insuranceAmount.signum() > 0 ? insuranceAmount : billed)
                        : BigDecimal.ZERO.setScale(MONEY_SCALE);

        return new Amounts(
                billed,
                approved,
                rejectedAmount,
                patientShare,
                insuranceAmount,
                paid,
                outstanding
        );
    }

    private BigDecimal patientShareFromChargeLines(
            List<ClaimItem> items,
            SettlementLookups lookups
    ) {
        return items.stream()
                .map(ClaimItem::getBillingChargeLineId)
                .filter(Objects::nonNull)
                .map(lookups.chargeLines::get)
                .filter(Objects::nonNull)
                .map(BillingChargeLine::getPatientResponsibilityAmount)
                .map(this::money)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add);
    }

    private List<FinancialDocumentItem> documentItemsFor(
            ClaimRequest claim,
            List<ClaimItem> items,
            SettlementLookups lookups
    ) {
        List<FinancialDocumentItem> linked =
                items.stream()
                        .map(ClaimItem::getFinancialDocumentItemId)
                        .filter(Objects::nonNull)
                        .map(lookups.documentItemsById::get)
                        .filter(Objects::nonNull)
                        .toList();

        if (!linked.isEmpty()) {
            return linked;
        }

        return lookups.documentItemsByDocument.getOrDefault(
                claim.getFinancialDocumentId(),
                List.of()
        );
    }

    private String resolveInsuranceCompany(
            PatientInsurance insurance,
            SettlementLookups lookups
    ) {
        if (insurance == null) {
            return null;
        }

        String storedName = firstNonBlank(insurance.getPayerName());
        if (storedName != null) {
            return storedName;
        }

        return displayName(insurance.getPayerNphiesId(), lookups);
    }

    private String resolveTpa(
            PatientInsurance insurance,
            SettlementLookups lookups
    ) {
        if (insurance == null) {
            return null;
        }

        String storedName = firstNonBlank(insurance.getTpaName());
        if (storedName != null) {
            return storedName;
        }

        String tpaNphiesId = firstNonBlank(insurance.getTpaNphiesId());
        if (tpaNphiesId == null) {
            PayorDTO payor =
                    payorHelper.findPayor(
                            insurance.getPayorId(),
                            insurance.getPayerNphiesId()
                    );
            tpaNphiesId = payor == null ? null : firstNonBlank(payor.tpaNphiesId());
        }

        return displayName(tpaNphiesId, lookups);
    }

    private String displayName(String nphiesId, SettlementLookups lookups) {
        if (!hasText(nphiesId)) {
            return null;
        }

        return lookups.payerNames.computeIfAbsent(
                nphiesId.trim(),
                id -> nphiesPayerHelper.resolvePayerDisplayName(id, null)
        );
    }

    private String settlementStatus(ClaimStatus status, Amounts amounts) {
        if (REJECTED_STATUSES.contains(status)) {
            return "REJECTED";
        }

        if (amounts.paidAmount.signum() > 0 && amounts.outstandingAmount.signum() <= 0) {
            return "SETTLED";
        }

        if (amounts.paidAmount.signum() > 0) {
            return "PARTIALLY_SETTLED";
        }

        return "UNSETTLED";
    }

    private EncounterType parseEncounterType(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return EncounterType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Unknown encounter type: " + value,
                    ENTITY_NAME,
                    "encounterType.invalid"
            );
        }
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id")
        );
    }

    private BigDecimal sum(
            List<ClaimItem> items,
            Function<ClaimItem, BigDecimal> getter
    ) {
        return items.stream()
                .map(getter)
                .map(this::money)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add);
    }

    private BigDecimal sumDocument(
            List<FinancialDocumentItem> items,
            Function<FinancialDocumentItem, BigDecimal> getter
    ) {
        return items.stream()
                .map(getter)
                .map(this::money)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SettlementLookups(
            Map<Long, List<ClaimItem>> itemsByClaim,
            Map<Long, PatientEncounter> encounters,
            Map<Long, PatientInsurance> insurances,
            Map<Long, BillingChargeLine> chargeLines,
            Map<Long, FinancialDocumentItem> documentItemsById,
            Map<Long, List<FinancialDocumentItem>> documentItemsByDocument,
            Map<String, String> payerNames
    ) {
    }

    private record Amounts(
            BigDecimal billedAmount,
            BigDecimal approvedAmount,
            BigDecimal rejectedAmount,
            BigDecimal patientShare,
            BigDecimal insuranceAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount
    ) {
    }
}
