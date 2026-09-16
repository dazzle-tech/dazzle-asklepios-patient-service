package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.CoverageContractClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CoverageContractShareService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverageContractShareService.class);
    private static final int MONEY_SCALE = 4;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CoverageContractClient coverageContractClient;
    private final PayorClient payorClient;
    private final InsuranceCalculationService insuranceCalculationService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    /**
     * Non-Wasel path: Coverages first (insurance covered amount), then existing copayment
     * math on the covered portion only. If Coverages are not configured, copayment still
     * runs on the full net so current numbers stay unchanged.
     */
    public Optional<InsuranceSplit> calculateSplit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount
    ) {
        BigDecimal normalizedNet = money(netAmount);
        if (insurance == null || insurance.getId() == null || normalizedNet.signum() <= 0) {
            return Optional.empty();
        }

        CoverageContractResolveDtos.Response resolved = resolve(insurance, item);
        if (resolved == null || !resolved.matched()) {
            return Optional.empty();
        }

        boolean uncovered = Boolean.TRUE.equals(resolved.uncovered());
        boolean excluded = isExcluded(resolved);
        boolean cashOut = uncovered || excluded;
        BigDecimal billedNet = applyPriceDiscount(normalizedNet, resolved, cashOut, excluded);

        if (cashOut) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Item is cash-out patientInsuranceId={} contractId={} uncovered={} excluded={}",
                    insurance.getId(),
                    contractId(resolved),
                    uncovered,
                    excluded
            );
            InsuranceSplit cashSplit = new InsuranceSplit(
                    billedNet,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
            return Optional.of(applyPeriodLimits(cashSplit, resolved, insurance, item, billedNet, true));
        }

        BigDecimal coveredAmount = coveredAmount(billedNet, resolved.coverage());
        BigDecimal uncoveredAmount = money(billedNet.subtract(coveredAmount));
        InsuranceSplit coveredSplit = splitCoveredAmount(coveredAmount, resolved.copayment());
        if (coveredSplit == null) {
            if (resolved.coverage() == null) {
                return Optional.empty();
            }
            coveredSplit = new InsuranceSplit(
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    coveredAmount
            );
        }

        InsuranceSplit split = new InsuranceSplit(
                money(uncoveredAmount.add(coveredSplit.patientShare())),
                money(coveredSplit.insuranceShare())
        );
        LOG.info(
                "[COVERAGE_CONTRACT] Applied Coverages then copayment patientInsuranceId={} contractId={} covered={} uncovered={} patientShare={} insuranceShare={}",
                insurance.getId(),
                contractId(resolved),
                coveredAmount,
                uncoveredAmount,
                split.patientShare(),
                split.insuranceShare()
        );
        return Optional.of(applyPeriodLimits(split, resolved, insurance, item, billedNet, false));
    }

    /**
     * Wasel path: keep the existing Wasel/benefit-rule split, then cap insurance share
     * to the Coverages amount when a reading matches. Unmatched Coverages do not cash-out Wasel.
     */
    public InsuranceSplit capWithCoverage(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount,
            InsuranceSplit existingSplit
    ) {
        if (existingSplit == null) {
            return existingSplit;
        }
        BigDecimal normalizedNet = money(netAmount);
        CoverageContractResolveDtos.Response resolved = resolve(insurance, item);
        if (resolved == null || !resolved.matched()) {
            return existingSplit;
        }

        if (isExcluded(resolved)) {
            BigDecimal billedNet = applyPriceDiscount(normalizedNet, resolved, true, true);
            InsuranceSplit cashSplit = new InsuranceSplit(
                    billedNet,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
            LOG.info(
                    "[COVERAGE_CONTRACT] Excluded Wasel item is cash patientInsuranceId={} billedNet={}",
                    insurance.getId(),
                    billedNet
            );
            return applyPeriodLimits(cashSplit, resolved, insurance, item, billedNet, true);
        }

        BigDecimal billedNet = applyPriceDiscount(normalizedNet, resolved, false, false);
        InsuranceSplit split = scaleSplit(existingSplit, normalizedNet, billedNet);
        if (resolved.coverage() != null) {
            BigDecimal coveredAmount = coveredAmount(billedNet, resolved.coverage());
            BigDecimal insuranceShare = money(split.insuranceShare()).min(coveredAmount);
            split = new InsuranceSplit(
                    money(billedNet.subtract(insuranceShare)),
                    insuranceShare
            );
            LOG.info(
                    "[COVERAGE_CONTRACT] Capped Wasel insurance share with Coverages patientInsuranceId={} contractId={} covered={} patientShare={} insuranceShare={}",
                    insurance.getId(),
                    contractId(resolved),
                    coveredAmount,
                    split.patientShare(),
                    split.insuranceShare()
            );
        }
        return applyPeriodLimits(split, resolved, insurance, item, billedNet, false);
    }

    /**
     * Apply Coverage Limit then Cash Limit after the existing share math.
     * If a period ceiling still has room, the numbers stay unchanged.
     */
    public InsuranceSplit applyLimit(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount,
            InsuranceSplit existingSplit
    ) {
        if (existingSplit == null) {
            return existingSplit;
        }
        CoverageContractResolveDtos.Response resolved = resolve(insurance, item);
        BigDecimal originalNet = money(netAmount);
        if (isExcluded(resolved)) {
            BigDecimal billedNet = applyPriceDiscount(originalNet, resolved, true, true);
            InsuranceSplit cashSplit = new InsuranceSplit(
                    billedNet,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
            return applyPeriodLimits(cashSplit, resolved, insurance, item, billedNet, true);
        }
        BigDecimal billedNet = applyPriceDiscount(originalNet, resolved, false, false);
        InsuranceSplit split = scaleSplit(existingSplit, originalNet, billedNet);
        return applyPeriodLimits(split, resolved, insurance, item, billedNet, false);
    }

    public boolean requiresPreApproval(PatientInsurance insurance, PatientServiceAndProduct item) {
        if (insurance == null) {
            return false;
        }
        CoverageContractResolveDtos.Response resolved = resolve(insurance, item);
        if (resolved == null || !resolved.matched() || resolved.preApproval() == null) {
            return false;
        }
        if (isExcluded(resolved)) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Pre-approval skipped because item is excluded patientInsuranceId={}",
                    insurance.getId()
            );
            return false;
        }
        LOG.info(
                "[COVERAGE_CONTRACT] Pre-approval required by contract terms patientInsuranceId={} contractId={} preApprovalId={}",
                insurance.getId(),
                contractId(resolved),
                resolved.preApproval().preApprovalId()
        );
        return true;
    }

    private InsuranceSplit applyPeriodLimits(
            InsuranceSplit split,
            CoverageContractResolveDtos.Response resolved,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal normalizedNet,
            boolean uncovered
    ) {
        InsuranceSplit afterCoverageLimit = applyLimit(split, resolved, insurance, item, normalizedNet);
        InsuranceSplit afterCashLimit = applyCashLimit(afterCoverageLimit, resolved, insurance, item, normalizedNet, uncovered);
        return applyExceededCashDiscount(afterCashLimit, resolved, insurance, item, normalizedNet);
    }

    private InsuranceSplit applyLimit(
            InsuranceSplit split,
            CoverageContractResolveDtos.Response resolved,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal normalizedNet
    ) {
        if (split == null || resolved == null || !resolved.matched() || resolved.limit() == null) {
            return split;
        }
        if (resolved.limit().limitValue() == null || item == null) {
            return split;
        }

        BigDecimal consumed = consumedBasis(item, insurance, resolved.limit(), false);
        BigDecimal currentBasis = basisAmount(item, resolved.limit(), normalizedNet);
        BigDecimal limitAmount = periodLimitAmount(resolved.limit(), consumed, currentBasis);
        BigDecimal remaining = money(limitAmount.subtract(consumed));
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal insuranceShare = money(split.insuranceShare());
        if (insuranceShare.compareTo(remaining) <= 0) {
            return split;
        }

        InsuranceSplit capped = new InsuranceSplit(
                money(normalizedNet.subtract(remaining)),
                remaining
        );
        LOG.info(
                "[COVERAGE_CONTRACT] Capped insurance share with Coverage Limit patientInsuranceId={} contractId={} limit={} consumed={} remaining={} patientShare={} insuranceShare={}",
                insurance.getId(),
                contractId(resolved),
                limitAmount,
                consumed,
                remaining,
                capped.patientShare(),
                capped.insuranceShare()
        );
        return capped;
    }

    private InsuranceSplit applyCashLimit(
            InsuranceSplit split,
            CoverageContractResolveDtos.Response resolved,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal normalizedNet,
            boolean uncovered
    ) {
        if (split == null || resolved == null || !resolved.matched() || resolved.cashLimit() == null) {
            return split;
        }
        if (resolved.cashLimit().limitValue() == null || item == null) {
            return split;
        }

        BigDecimal remainingCash = remainingCashRoom(item, insurance, resolved.cashLimit(), normalizedNet);
        BigDecimal patientShare = money(split.patientShare());
        if (patientShare.compareTo(remainingCash) <= 0) {
            return split;
        }

        if (uncovered) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Cash Limit exceeded on uncovered item; extra cash kept patientInsuranceId={} remainingCash={}",
                    insurance.getId(),
                    remainingCash
            );
            return split;
        }

        BigDecimal maxInsurance = normalizedNet;
        if (resolved.limit() != null) {
            BigDecimal coverageRemaining = remainingRoom(
                    item,
                    insurance,
                    resolved.limit(),
                    basisAmount(item, resolved.limit(), normalizedNet)
            );
            if (coverageRemaining != null) {
                maxInsurance = coverageRemaining;
            }
        }

        BigDecimal newPatientShare = remainingCash;
        BigDecimal newInsuranceShare = money(normalizedNet.subtract(newPatientShare));
        if (newInsuranceShare.compareTo(maxInsurance) > 0) {
            newInsuranceShare = money(maxInsurance);
            newPatientShare = money(normalizedNet.subtract(newInsuranceShare));
        }

        if (patientShare.compareTo(newPatientShare) == 0
                && money(split.insuranceShare()).compareTo(newInsuranceShare) == 0) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Cash Limit exceeded; extra cash kept because Coverage Limit cannot take the excess patientInsuranceId={} remainingCash={} maxInsurance={}",
                    insurance.getId(),
                    remainingCash,
                    maxInsurance
            );
            return split;
        }

        if (resolved.copayment() != null && Boolean.TRUE.equals(resolved.copayment().discountOnExceededCash())) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Cash Limit excess is eligible for discount-on-exceeded-cash; Discount terms are not applied yet patientInsuranceId={}",
                    insurance.getId()
            );
        }

        InsuranceSplit capped = new InsuranceSplit(newPatientShare, newInsuranceShare);
        LOG.info(
                "[COVERAGE_CONTRACT] Capped patient cash with Cash Limit patientInsuranceId={} contractId={} remainingCash={} patientShare={} insuranceShare={}",
                insurance.getId(),
                contractId(resolved),
                remainingCash,
                capped.patientShare(),
                capped.insuranceShare()
        );
        return capped;
    }

    private BigDecimal applyPriceDiscount(
            BigDecimal normalizedNet,
            CoverageContractResolveDtos.Response resolved,
            boolean cashOut,
            boolean excluded
    ) {
        if (resolved == null || resolved.discount() == null || resolved.discount().discountValue() == null) {
            return normalizedNet;
        }
        if (cashOut && !shouldDiscountCashItem(resolved, excluded)) {
            return normalizedNet;
        }
        BigDecimal discount = discountAmount(normalizedNet, resolved.discount());
        BigDecimal billedNet = money(normalizedNet.subtract(discount));
        if (billedNet.signum() < 0) {
            billedNet = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (billedNet.compareTo(normalizedNet) != 0) {
            LOG.info(
                    "[COVERAGE_CONTRACT] Applied Discount before share split contractId={} originalNet={} billedNet={} discount={}",
                    contractId(resolved),
                    normalizedNet,
                    billedNet,
                    discount
            );
        }
        return billedNet;
    }

    private boolean shouldDiscountCashItem(CoverageContractResolveDtos.Response resolved, boolean excluded) {
        if (resolved.copayment() == null) {
            return false;
        }
        if (excluded) {
            return Boolean.TRUE.equals(resolved.copayment().discountOnExcluded());
        }
        return Boolean.TRUE.equals(resolved.copayment().discountOnCash());
    }

    private boolean isExcluded(CoverageContractResolveDtos.Response resolved) {
        return resolved != null
                && resolved.exclusion() != null
                && "YES".equalsIgnoreCase(resolved.exclusion().excludedResult());
    }

    private InsuranceSplit applyExceededCashDiscount(
            InsuranceSplit split,
            CoverageContractResolveDtos.Response resolved,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal billedNet
    ) {
        if (split == null
                || resolved == null
                || resolved.discount() == null
                || resolved.cashLimit() == null
                || resolved.copayment() == null
                || !Boolean.TRUE.equals(resolved.copayment().discountOnExceededCash())) {
            return split;
        }
        BigDecimal remainingCash = remainingCashRoom(item, insurance, resolved.cashLimit(), billedNet);
        BigDecimal patientShare = money(split.patientShare());
        if (patientShare.compareTo(remainingCash) <= 0) {
            return split;
        }
        BigDecimal extraCash = money(patientShare.subtract(remainingCash));
        BigDecimal writeOff = discountAmount(extraCash, resolved.discount());
        if (writeOff.signum() <= 0) {
            return split;
        }
        BigDecimal newPatientShare = money(patientShare.subtract(writeOff));
        if (newPatientShare.compareTo(remainingCash) < 0) {
            newPatientShare = remainingCash;
        }
        LOG.info(
                "[COVERAGE_CONTRACT] Applied discount on exceeded cash patientInsuranceId={} extraCash={} writeOff={} patientShare={}",
                insurance.getId(),
                extraCash,
                writeOff,
                newPatientShare
        );
        return new InsuranceSplit(newPatientShare, money(split.insuranceShare()));
    }

    private InsuranceSplit scaleSplit(InsuranceSplit split, BigDecimal originalNet, BigDecimal billedNet) {
        if (split == null || billedNet.compareTo(originalNet) == 0) {
            return split;
        }
        if (originalNet.signum() <= 0) {
            return new InsuranceSplit(
                    billedNet,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
        }
        BigDecimal patientShare = money(
                split.patientShare().multiply(billedNet).divide(originalNet, MONEY_SCALE, RoundingMode.HALF_UP)
        );
        if (patientShare.compareTo(billedNet) > 0) {
            patientShare = billedNet;
        }
        return new InsuranceSplit(patientShare, money(billedNet.subtract(patientShare)));
    }

    private BigDecimal discountAmount(
            BigDecimal amount,
            CoverageContractResolveDtos.DiscountSnapshot discount
    ) {
        if (discount == null || discount.discountValue() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if ("FIXED_AMOUNT".equalsIgnoreCase(discount.discountType())
                || "FIXED".equalsIgnoreCase(discount.discountType())) {
            return money(discount.discountValue()).min(money(amount));
        }
        BigDecimal percent = money(discount.discountValue());
        return money(amount.multiply(percent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)).min(money(amount));
    }

    private BigDecimal remainingRoom(
            PatientServiceAndProduct item,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot reading,
            BigDecimal currentBasis
    ) {
        if (reading == null || reading.limitValue() == null || item == null) {
            return null;
        }
        BigDecimal consumed = consumedBasis(item, insurance, reading, false);
        BigDecimal limitAmount = periodLimitAmount(reading, consumed, currentBasis);
        BigDecimal remaining = money(limitAmount.subtract(consumed));
        if (remaining.signum() < 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return remaining;
    }

    private BigDecimal remainingCashRoom(
            PatientServiceAndProduct item,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot cashLimit,
            BigDecimal normalizedNet
    ) {
        BigDecimal consumedCash = consumedCash(item, insurance, cashLimit);
        BigDecimal currentBasis = basisAmount(item, cashLimit, normalizedNet);
        BigDecimal consumedPeriodBasis = consumedBasis(item, insurance, cashLimit, true);
        BigDecimal cap = periodLimitAmount(cashLimit, consumedPeriodBasis, currentBasis);
        BigDecimal remaining = money(cap.subtract(consumedCash));
        if (remaining.signum() < 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return remaining;
    }

    private BigDecimal consumedCash(
            PatientServiceAndProduct item,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot cashLimit
    ) {
        BigDecimal consumed = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (PatientServiceAndProduct previous : periodItems(item, insurance, cashLimit, true)) {
            if (!countsTowardCashLimit(previous, item, insurance, cashLimit)) {
                continue;
            }
            consumed = consumed.add(money(previous.getPatientShareAmount()));
        }
        return money(consumed);
    }

    private BigDecimal consumedBasis(
            PatientServiceAndProduct item,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot limit,
            boolean cashPool
    ) {
        BigDecimal consumed = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (PatientServiceAndProduct previous : periodItems(item, insurance, limit, cashPool)) {
            if (cashPool) {
                if (!countsTowardCashLimit(previous, item, insurance, limit)) {
                    continue;
                }
            } else if (!countsTowardLimit(previous, item, insurance, limit)) {
                continue;
            }
            consumed = consumed.add(basisAmount(previous, limit, previous.getNetAmount()));
        }
        return money(consumed);
    }

    private List<PatientServiceAndProduct> periodItems(
            PatientServiceAndProduct item,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot limit,
            boolean cashPool
    ) {
        if ("PER_DAY".equalsIgnoreCase(limit.periodBasis())) {
            LocalDate encounterDate = encounterDate(item);
            if (item.getPatientId() == null || encounterDate == null) {
                return List.of();
            }
            if (cashPool) {
                return patientServiceAndProductRepository.findByPatientAndEncounterDate(
                        item.getPatientId(),
                        encounterDate
                );
            }
            if (insurance.getId() == null) {
                return List.of();
            }
            return patientServiceAndProductRepository.findByPatientInsuranceAndEncounterDate(
                    item.getPatientId(),
                    insurance.getId(),
                    encounterDate
            );
        }
        if (item.getEncounterId() == null) {
            return List.of();
        }
        return patientServiceAndProductRepository.findByEncounterId(item.getEncounterId());
    }

    private LocalDate encounterDate(PatientServiceAndProduct item) {
        if (item.getEncounterId() == null) {
            return null;
        }
        return patientEncounterRepository.findById(item.getEncounterId())
                .map(PatientEncounter::getEncounterDate)
                .orElse(null);
    }

    private boolean countsTowardLimit(
            PatientServiceAndProduct previous,
            PatientServiceAndProduct current,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot limit
    ) {
        if (previous == null) {
            return false;
        }
        if (current.getId() != null && Objects.equals(previous.getId(), current.getId())) {
            return false;
        }
        if (previous.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return false;
        }
        if (previous.isUncoveredCashItem()) {
            return false;
        }
        if (previous.getPatientInsuranceId() == null) {
            return false;
        }
        if (insurance.getId() != null && !insurance.getId().equals(previous.getPatientInsuranceId())) {
            return false;
        }
        return matchesReading(previous, limit);
    }

    private boolean countsTowardCashLimit(
            PatientServiceAndProduct previous,
            PatientServiceAndProduct current,
            PatientInsurance insurance,
            CoverageContractResolveDtos.CoverageReadingSnapshot cashLimit
    ) {
        if (previous == null) {
            return false;
        }
        if (current.getId() != null && Objects.equals(previous.getId(), current.getId())) {
            return false;
        }
        if (previous.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return false;
        }
        if (previous.getPatientInsuranceId() != null
                && insurance.getId() != null
                && !insurance.getId().equals(previous.getPatientInsuranceId())
                && !previous.isUncoveredCashItem()) {
            return false;
        }
        return matchesReading(previous, cashLimit);
    }

    private boolean matchesReading(
            PatientServiceAndProduct item,
            CoverageContractResolveDtos.CoverageReadingSnapshot reading
    ) {
        if (reading.serviceId() != null) {
            return reading.serviceId().equals(catalogItemId(item));
        }
        if (reading.billingItemType() != null && !reading.billingItemType().isBlank()) {
            return item.getBillingItemType() != null
                    && reading.billingItemType().equalsIgnoreCase(item.getBillingItemType().name());
        }
        return true;
    }

    private BigDecimal basisAmount(
            PatientServiceAndProduct item,
            CoverageContractResolveDtos.CoverageReadingSnapshot limit,
            BigDecimal fallbackNet
    ) {
        if ("GROSS".equalsIgnoreCase(limit.coverageBasis())
                && item.getGrossAmount() != null
                && item.getGrossAmount().signum() > 0) {
            return money(item.getGrossAmount());
        }
        return money(fallbackNet);
    }

    private BigDecimal periodLimitAmount(
            CoverageContractResolveDtos.CoverageReadingSnapshot limit,
            BigDecimal consumed,
            BigDecimal currentBasis
    ) {
        if ("FIXED".equalsIgnoreCase(limit.valueType())) {
            return money(limit.limitValue());
        }
        BigDecimal percent = money(limit.limitValue());
        return money(consumed.add(currentBasis).multiply(percent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private InsuranceSplit splitCoveredAmount(
            BigDecimal coveredAmount,
            CoverageContractResolveDtos.CopaymentSnapshot copayment
    ) {
        if (coveredAmount.signum() <= 0) {
            return new InsuranceSplit(
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
        }
        if (copayment == null || copayment.valueAmount() == null) {
            return null;
        }
        return applyCopayment(coveredAmount, copayment);
    }

    private CoverageContractResolveDtos.Response resolve(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        try {
            return coverageContractClient.resolve(toRequest(insurance, item));
        } catch (FeignException exception) {
            LOG.warn(
                    "[COVERAGE_CONTRACT] Unable to resolve coverage contract patientInsuranceId={}",
                    insurance.getId(),
                    exception
            );
            return null;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[COVERAGE_CONTRACT] Coverage contract resolution failed patientInsuranceId={}",
                    insurance.getId(),
                    exception
            );
            return null;
        }
    }

    private CoverageContractResolveDtos.Request toRequest(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        PatientEncounter encounter = item == null || item.getEncounterId() == null
                ? null
                : patientEncounterRepository.findById(item.getEncounterId()).orElse(null);
        List<Long> diagnosisIds = item == null || item.getEncounterId() == null
                ? List.of()
                : patientDiagnosisRepository.findByEncounterId(item.getEncounterId()).stream()
                        .map(PatientDiagnosis::getDiagnosisId)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        return new CoverageContractResolveDtos.Request(
                null,
                resolvePayerNphiesId(insurance),
                null,
                firstNonBlank(insurance.getTpaName()),
                firstNonBlank(insurance.getPolicyNumber()),
                firstNonBlank(insurance.getPolicyClassName()),
                encounter == null || encounter.getEncounterType() == null
                        ? null
                        : encounter.getEncounterType().name(),
                LocalDate.now(),
                encounter == null ? null : encounter.getFacilityId(),
                encounter == null ? null : encounter.getDepartmentId(),
                diagnosisIds,
                item == null || item.getBillingItemType() == null ? null : item.getBillingItemType().name(),
                catalogItemId(item)
        );
    }

    private Long catalogItemId(PatientServiceAndProduct item) {
        if (item == null || item.getBillingItemType() == null) {
            return null;
        }
        BillingItemTypes type = item.getBillingItemType();
        if (type == BillingItemTypes.MEDICATION) {
            return item.getBrandMedicationId();
        }
        if (type == BillingItemTypes.LABORATORY
                || type == BillingItemTypes.RADIOLOGY
                || type == BillingItemTypes.PATHOLOGY) {
            return item.getDiagnosticTestId();
        }
        if (type == BillingItemTypes.PROCEDURE) {
            return item.getProcedureId();
        }
        return item.getServiceId();
    }

    private String resolvePayerNphiesId(PatientInsurance insurance) {
        String nphiesId = firstNonBlank(insurance.getPayerNphiesId());
        if (nphiesId != null || insurance.getPayorId() == null) {
            return nphiesId;
        }
        try {
            PayorDTO payor = payorClient.getPayorById(insurance.getPayorId());
            return payor == null ? null : firstNonBlank(payor.nphiesId());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[COVERAGE_CONTRACT] Unable to resolve payer NPHIES id payorId={}",
                    insurance.getPayorId(),
                    exception
            );
            return null;
        }
    }

    private BigDecimal coveredAmount(
            BigDecimal normalizedNet,
            CoverageContractResolveDtos.CoverageReadingSnapshot coverage
    ) {
        if (coverage == null || coverage.limitValue() == null) {
            return normalizedNet;
        }
        if ("FIXED".equalsIgnoreCase(coverage.valueType())) {
            return money(coverage.limitValue()).min(normalizedNet);
        }
        BigDecimal percent = money(coverage.limitValue());
        return money(normalizedNet.multiply(percent).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP))
                .min(normalizedNet);
    }

    private InsuranceSplit applyCopayment(
            BigDecimal normalizedNet,
            CoverageContractResolveDtos.CopaymentSnapshot copayment
    ) {
        if ("FIXED".equalsIgnoreCase(copayment.valueType())) {
            BigDecimal patientShare = money(copayment.valueAmount()).min(normalizedNet);
            return new InsuranceSplit(patientShare, money(normalizedNet.subtract(patientShare)));
        }

        return insuranceCalculationService.calculateSplit(
                normalizedNet,
                copayment.valueAmount(),
                BigDecimal.ZERO
        );
    }

    private Long contractId(CoverageContractResolveDtos.Response resolved) {
        return resolved.contract() == null ? null : resolved.contract().id();
    }

    private String firstNonBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
