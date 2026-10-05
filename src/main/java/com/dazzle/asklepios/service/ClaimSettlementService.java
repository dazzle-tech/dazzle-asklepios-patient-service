package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.ClaimItem;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.ClaimItemRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimSettlementService {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimSettlementService.class);

    private static final String ENTITY_NAME = "claimSettlement";

    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimItemRepository claimItemRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientRepository patientRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final PayorHelper payorHelper;
    private final NphiesPayerHelper nphiesPayerHelper;

    public List<String> listSettlementNumbers(
            String payerNphiesId,
            String encounterTypeValue,
            Instant fromDate,
            Instant toDate
    ) {
        return matchingClaims(payerNphiesId, encounterTypeValue, fromDate, toDate, null, Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(this::displaySettlementNo)
                .distinct()
                .sorted(this::newestSettlementFirst)
                .toList();
    }

    public Page<ClaimSettlementRowResponse> search(
            String payerNphiesId,
            String encounterTypeValue,
            Instant fromDate,
            Instant toDate,
            String settlementNo,
            Pageable pageable
    ) {
        LOG.debug(
                "[CLAIM_SETTLEMENT] Search payerNphiesId={} encounterType={} from={} to={} settlementNo={}",
                payerNphiesId,
                encounterTypeValue,
                fromDate,
                toDate,
                settlementNo
        );

        Pageable sorted = withDefaultSort(pageable);
        List<ClaimRequest> filtered = matchingClaims(
                payerNphiesId,
                encounterTypeValue,
                fromDate,
                toDate,
                settlementNo,
                sorted.getSort()
        );
        Map<Long, PatientEncounter> encounters = loadEncounters(filtered);
        Map<Long, PatientInsurance> insurances = loadInsurances(filtered, encounters);

        int start = (int) sorted.getOffset();
        int end = Math.min(start + sorted.getPageSize(), filtered.size());
        List<ClaimRequest> pageClaims = start >= filtered.size()
                ? List.of()
                : filtered.subList(start, end);

        if (pageClaims.isEmpty()) {
            return new PageImpl<>(List.of(), sorted, filtered.size());
        }

        SettlementLookups lookups = loadLookups(pageClaims, encounters, insurances);
        List<ClaimSettlementRowResponse> rows = pageClaims.stream()
                .map(claim -> toRow(claim, lookups))
                .toList();

        return new PageImpl<>(rows, sorted, filtered.size());
    }

    private List<ClaimRequest> matchingClaims(
            String payerNphiesId,
            String encounterTypeValue,
            Instant fromDate,
            Instant toDate,
            String settlementNo,
            Sort sort
    ) {
        EncounterType encounterType = parseEncounterType(encounterTypeValue);
        Long payorId = payorHelper.resolvePayorId(null, payerNphiesId);
        List<ClaimRequest> acceptedClaims = claimRequestRepository.findByStatus(ClaimStatus.ACCEPTED, sort);
        Map<Long, PatientEncounter> encounters = loadEncounters(acceptedClaims);
        Map<Long, PatientInsurance> insurances = loadInsurances(acceptedClaims, encounters);

        return acceptedClaims.stream()
                .filter(claim -> matchesPayer(claim, encounters, insurances, payerNphiesId, payorId))
                .filter(claim -> matchesEncounterType(claim, encounters, encounterType))
                .filter(claim -> matchesSettlementDate(claim, fromDate, toDate))
                .filter(claim -> matchesSettlementNo(claim, settlementNo))
                .toList();
    }

    private boolean matchesSettlementNo(ClaimRequest claim, String settlementNo) {
        if (!hasText(settlementNo)) {
            return true;
        }
        String actual = displaySettlementNo(claim);
        return actual != null && actual.equalsIgnoreCase(settlementNo.trim());
    }

    private String displaySettlementNo(ClaimRequest claim) {
        return firstNonBlank(claim.getSettlementNo(), "SET-" + claim.getId());
    }

    private int newestSettlementFirst(String left, String right) {
        Long leftNo = settlementSequence(left);
        Long rightNo = settlementSequence(right);
        if (leftNo != null && rightNo != null && !leftNo.equals(rightNo)) {
            return rightNo.compareTo(leftNo);
        }
        return right.compareToIgnoreCase(left);
    }

    private Long settlementSequence(String settlementNo) {
        if (!hasText(settlementNo)) {
            return null;
        }
        int dash = settlementNo.lastIndexOf('-');
        if (dash < 0 || dash == settlementNo.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(settlementNo.substring(dash + 1).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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

        PatientInsurance insurance = insurances.get(insuranceIdFor(claim, encounters));
        if (insurance == null) {
            return false;
        }

        if (hasText(payerNphiesId) && payerNphiesId.equalsIgnoreCase(insurance.getPayerNphiesId())) {
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

    private boolean matchesSettlementDate(ClaimRequest claim, Instant fromDate, Instant toDate) {
        Instant settlementDate = settlementDate(claim);
        if (fromDate != null && (settlementDate == null || settlementDate.isBefore(fromDate))) {
            return false;
        }
        return toDate == null || (settlementDate != null && settlementDate.isBefore(toDate));
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

        Map<Long, Patient> patients = loadPatients(claims);
        Map<Long, FinancialDocument> documents =
                documentIds.isEmpty()
                        ? Map.of()
                        : financialDocumentRepository.findAllById(documentIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                FinancialDocument::getId,
                                                Function.identity()
                                        )
                                );

        return new SettlementLookups(
                itemsByClaim,
                encounters,
                insurances,
                chargeLines,
                documentItemsById,
                documentItemsByDocument,
                patients,
                documents,
                new HashMap<>(),
                preloadPayors(insurances)
        );
    }

    private Map<String, PayorDTO> preloadPayors(Map<Long, PatientInsurance> insurances) {
        Map<String, PayorDTO> payors = new HashMap<>();
        for (PatientInsurance insurance : insurances.values()) {
            if (insurance == null || hasText(insurance.getTpaName()) || hasText(insurance.getTpaNphiesId())) {
                continue;
            }
            String cacheKey = payorCacheKey(insurance.getPayorId(), insurance.getPayerNphiesId());
            if (payors.containsKey(cacheKey)) {
                continue;
            }
            PayorDTO payor = payorHelper.findPayor(insurance.getPayorId(), insurance.getPayerNphiesId());
            payors.put(cacheKey, payor);
        }
        return payors;
    }

    private String payorCacheKey(Long payorId, String payerNphiesId) {
        return (payorId == null ? "" : payorId) + "|" + (payerNphiesId == null ? "" : payerNphiesId.trim());
    }

    private ClaimSettlementAmounts.Amounts amountsFor(
            ClaimRequest claim,
            List<ClaimItem> items,
            SettlementLookups lookups
    ) {
        return ClaimSettlementAmounts.forClaim(
                claim,
                items,
                lookups.chargeLines,
                lookups.documentItemsById,
                lookups.documentItemsByDocument
        );
    }

    private Map<Long, Patient> loadPatients(List<ClaimRequest> claims) {
        Set<Long> patientIds =
                claims.stream()
                        .map(ClaimRequest::getPatientId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (patientIds.isEmpty()) {
            return Map.of();
        }

        return patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
    }

    private ClaimSettlementRowResponse toRow(
            ClaimRequest claim,
            SettlementLookups lookups
    ) {
        List<ClaimItem> items =
                lookups.itemsByClaim.getOrDefault(claim.getId(), List.of());

        ClaimSettlementAmounts.Amounts amounts = amountsFor(claim, items, lookups);
        PatientInsurance insurance =
                lookups.insurances.get(insuranceIdFor(claim, lookups.encounters));
        Patient patient = lookups.patients.get(claim.getPatientId());
        PatientEncounter encounter = lookups.encounters.get(claim.getEncounterId());
        FinancialDocument invoice =
                lookups.documents.get(claim.getFinancialDocumentId());

        Instant claimDate = claim.getCreatedDate();
        Instant settlementDate = settlementDate(claim);

        return new ClaimSettlementRowResponse(
                claim.getId(),
                displaySettlementNo(claim),
                settlementDate,
                resolveInsuranceCompany(insurance, lookups),
                resolveTpa(insurance, lookups),
                firstNonBlank(claim.getProvClaimNo(), claim.getClaimReference()),
                claimDate,
                amounts.billedAmount(),
                amounts.approvedAmount(),
                amounts.rejectedAmount(),
                amounts.patientShare(),
                amounts.insuranceAmount(),
                amounts.paidAmount(),
                amounts.outstandingAmount(),
                ClaimSettlementAmounts.paymentStatus(amounts),
                claim.getPatientId(),
                patientName(patient),
                patient == null ? null : firstNonBlank(patient.getMedicalRecordNumber()),
                patient == null || patient.getSexAtBirth() == null
                        ? null
                        : patient.getSexAtBirth().name(),
                patient == null ? null : patient.getDateOfBirth(),
                invoice == null ? null : firstNonBlank(invoice.getDocumentNumber()),
                encounter == null ? null : firstNonBlank(encounter.getEncounterNumber()),
                encounter == null || encounter.getEncounterType() == null
                        ? null
                        : encounter.getEncounterType().name()
        );
    }

    private String patientName(Patient patient) {
        if (patient == null) {
            return null;
        }

        String fullName = Stream.of(
                        patient.getFirstName(),
                        patient.getSecondName(),
                        patient.getThirdName(),
                        patient.getLastName()
                )
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));

        return firstNonBlank(fullName);
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
            PayorDTO payor = lookups.payors.get(
                    payorCacheKey(insurance.getPayorId(), insurance.getPayerNphiesId())
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
            Map<Long, Patient> patients,
            Map<Long, FinancialDocument> documents,
            Map<String, String> payerNames,
            Map<String, PayorDTO> payors
    ) {
    }
}
