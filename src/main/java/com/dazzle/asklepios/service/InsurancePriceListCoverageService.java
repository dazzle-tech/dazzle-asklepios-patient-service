package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.dto.billing.EncounterCoverageDTO;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckRequest;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.InsuranceItemNotCoveredException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Insurance visits may only bill an item to insurance when eligibility is
 * in-force and the item exists on that insurance's price list.
 *
 * Otherwise the item is billed as cash: Self-Pay Price List first, then
 * the Setup item price. Cash / self-pay visits are unchanged.
 */
@Service
public class InsurancePriceListCoverageService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePriceListCoverageService.class);

    public static final String NOT_IN_INSURANCE_PRICE_LIST =
            "NOT_IN_INSURANCE_PRICE_LIST";

    public static final String ELIGIBILITY_NOT_IN_FORCE =
            "ELIGIBILITY_NOT_IN_FORCE";

    public static final String WARNING_MESSAGE =
            "This item is not covered by the patient's insurance because it is not on the insurance price list. "
                    + "If you continue, it will be charged as cash to the patient.";

    public static final String ELIGIBILITY_WARNING_MESSAGE =
            "The patient's insurance coverage is not in-force. "
                    + "This item will be billed from the self-pay price list, "
                    + "or from setup if no self-pay price exists.";

    private static final String ENTITY_NAME = "insuranceCoverage";

    private final EncounterCoverageService encounterCoverageService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;
    private final BillingEngineService billingEngineService;

    public InsurancePriceListCoverageService(
            EncounterCoverageService encounterCoverageService,
            PatientEncounterRepository patientEncounterRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            InsurancePatientShareCalculator insurancePatientShareCalculator,
            @Lazy BillingEngineService billingEngineService
    ) {
        this.encounterCoverageService = encounterCoverageService;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.insurancePatientShareCalculator = insurancePatientShareCalculator;
        this.billingEngineService = billingEngineService;
    }

    public static String warningMessageFor(String notCoveredReason) {
        if (ELIGIBILITY_NOT_IN_FORCE.equals(notCoveredReason)) {
            return ELIGIBILITY_WARNING_MESSAGE;
        }

        return WARNING_MESSAGE;
    }

    @Transactional(readOnly = true)
    public InsurancePriceListCoverageCheckResult check(
            InsurancePriceListCoverageCheckRequest request
    ) {
        if (request == null || request.encounterId() == null) {
            throw new BadRequestAlertException(
                    "Encounter is required to check insurance price-list coverage.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        return check(
                request.encounterId(),
                request.billingItemType(),
                request.serviceId(),
                request.procedureId(),
                request.diagnosticTestId(),
                request.brandMedicationId(),
                request.currency()
        );
    }

    @Transactional(readOnly = true)
    public InsurancePriceListCoverageCheckResult check(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long serviceId,
            Long procedureId,
            Long diagnosticTestId,
            Long brandMedicationId,
            Currency currency
    ) {
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounter.notfound"
                ));

        EncounterCoverageDTO coverage =
                encounterCoverageService.getEncounterCoverage(encounterId);

        Long sourceId = resolveSourceId(
                billingItemType,
                serviceId,
                procedureId,
                diagnosticTestId,
                brandMedicationId
        );

        if (coverage == null || !coverage.insuranceVisit()) {
            return coveredResult(
                    billingItemType,
                    sourceId,
                    currency,
                    null
            );
        }

        if (encounter.getFacilityId() == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to check insurance price-list coverage.",
                    ENTITY_NAME,
                    "encounter.facility.required"
            );
        }

        if (billingItemType == null || sourceId == null) {
            throw new BadRequestAlertException(
                    "Billing item type and catalog item are required to check insurance coverage.",
                    ENTITY_NAME,
                    "item.required"
            );
        }

        Currency effectiveCurrency = currency != null ? currency : Currency.SAR;

        PatientServiceAndProduct previewItem = PatientServiceAndProduct.builder()
                .patientId(encounter.getPatient() == null ? null : encounter.getPatient().getId())
                .encounterId(encounterId)
                .billingItemType(billingItemType)
                .serviceId(serviceId)
                .procedureId(procedureId)
                .diagnosticTestId(diagnosticTestId)
                .brandMedicationId(brandMedicationId)
                .sourceId(sourceId)
                .serviceSource(ServiceSource.SERVICE_AND_PRODUCT)
                .quantity(1L)
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.ZERO)
                .currency(effectiveCurrency)
                .patientInsuranceId(coverage.patientInsuranceId())
                .coverageStatus(CoverageStatus.COVERED)
                .isBilled(Boolean.FALSE)
                .isDefaultService(Boolean.FALSE)
                .isExempted(Boolean.FALSE)
                .preAuthorizationRequired(Boolean.FALSE)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        PatientInsurance insurance = loadInsurance(coverage.patientInsuranceId());
        if (!insurancePatientShareCalculator.isLatestCoverageInForce(insurance)) {
            LOG.warn(
                    "[INSURANCE_PL] Eligibility is not in-force. "
                            + "Pricing from self-pay price list then setup. "
                            + "encounterId={} type={} sourceId={} patientInsuranceId={}",
                    encounterId,
                    billingItemType,
                    sourceId,
                    coverage.patientInsuranceId()
            );
            return cashCoverageResult(
                    previewItem,
                    encounter.getFacilityId(),
                    billingItemType,
                    sourceId,
                    effectiveCurrency,
                    coverage.patientInsuranceId(),
                    ELIGIBILITY_NOT_IN_FORCE,
                    null
            );
        }

        ResolvedBillingPrice insurancePrice =
                billingEngineService.resolvePricing(
                        previewItem,
                        encounter.getFacilityId(),
                        BillingCoverageType.INSURANCE
                );

        if (insurancePrice.resolvedFromPriceList()) {
            LOG.info(
                    "[INSURANCE_PL] Item is on insurance price list. encounterId={} type={} sourceId={} priceListItemId={}",
                    encounterId,
                    billingItemType,
                    sourceId,
                    insurancePrice.priceListItemId()
            );
            return new InsurancePriceListCoverageCheckResult(
                    true,
                    true,
                    false,
                    null,
                    billingItemType,
                    sourceId,
                    insurancePrice.setupItemName(),
                    insurancePrice.setupItemCode(),
                    insurancePrice.unitPrice(),
                    insurancePrice.currency() != null
                            ? insurancePrice.currency()
                            : effectiveCurrency,
                    coverage.patientInsuranceId(),
                    null
            );
        }

        LOG.warn(
                "[INSURANCE_PL] Item is not on insurance price list. "
                        + "Pricing from self-pay price list then setup. "
                        + "encounterId={} type={} sourceId={}",
                encounterId,
                billingItemType,
                sourceId
        );

        return cashCoverageResult(
                previewItem,
                encounter.getFacilityId(),
                billingItemType,
                sourceId,
                effectiveCurrency,
                coverage.patientInsuranceId(),
                NOT_IN_INSURANCE_PRICE_LIST,
                insurancePrice
        );
    }

    public void requireCoveredOrAcknowledged(
            InsurancePriceListCoverageCheckResult check,
            Boolean acceptUncoveredAsCash
    ) {
        if (check == null || !check.requiresCashConfirmation()) {
            return;
        }

        if (AcceptUncoveredAsCashSupport.isAccepted(acceptUncoveredAsCash)) {
            LOG.info(
                    "[INSURANCE_PL] Doctor accepted cash billing for uncovered item. type={} sourceId={}",
                    check.billingItemType(),
                    check.sourceId()
            );
            return;
        }

        throw new InsuranceItemNotCoveredException(check);
    }

    public void requireAllCoveredOrAcknowledged(
            List<InsurancePriceListCoverageCheckResult> checks,
            Boolean acceptUncoveredAsCash
    ) {
        if (checks == null || checks.isEmpty()) {
            return;
        }

        List<InsurancePriceListCoverageCheckResult> uncovered = new ArrayList<>();
        for (InsurancePriceListCoverageCheckResult check : checks) {
            if (check != null && check.requiresCashConfirmation()) {
                uncovered.add(check);
            }
        }

        if (uncovered.isEmpty()) {
            return;
        }

        if (AcceptUncoveredAsCashSupport.isAccepted(acceptUncoveredAsCash)) {
            LOG.info(
                    "[INSURANCE_PL] Doctor accepted cash billing for {} uncovered items",
                    uncovered.size()
            );
            return;
        }

        if (uncovered.size() == 1) {
            throw new InsuranceItemNotCoveredException(uncovered.get(0));
        }

        throw new InsuranceItemNotCoveredException(uncovered);
    }

    public void applyUncoveredCash(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            InsurancePriceListCoverageCheckResult check
    ) {
        if (builder == null || check == null || !check.requiresCashConfirmation()) {
            return;
        }

        builder
                .coverageStatus(CoverageStatus.NOT_COVERED)
                .notCoveredReason(resolveNotCoveredReason(check))
                .patientInsuranceId(check.patientInsuranceId())
                .preAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED)
                .preAuthorizationRequired(false)
                .paymentStatus(PaymentStatus.PENDING)
                .waseelSbsMappingId(null)
                .waseelSbsCode(null);
    }

    public void applyUncoveredCash(
            PatientServiceAndProduct item,
            InsurancePriceListCoverageCheckResult check
    ) {
        if (item == null || check == null || !check.requiresCashConfirmation()) {
            return;
        }

        item.setCoverageStatus(CoverageStatus.NOT_COVERED);
        item.setNotCoveredReason(resolveNotCoveredReason(check));
        item.setPatientInsuranceId(check.patientInsuranceId());
        item.setPreAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED);
        item.setPreAuthorizationRequired(false);
        item.setPaymentStatus(PaymentStatus.PENDING);
        item.setWaseelSbsMappingId(null);
        item.setWaseelSbsCode(null);
    }

    private InsurancePriceListCoverageCheckResult cashCoverageResult(
            PatientServiceAndProduct previewItem,
            Long facilityId,
            BillingItemTypes billingItemType,
            Long sourceId,
            Currency effectiveCurrency,
            Long patientInsuranceId,
            String notCoveredReason,
            ResolvedBillingPrice insurancePrice
    ) {
        ResolvedBillingPrice cashPrice =
                billingEngineService.resolvePricing(
                        previewItem,
                        facilityId,
                        BillingCoverageType.SELF_PAY
                );

        String itemName = firstNonBlank(
                cashPrice.setupItemName(),
                insurancePrice == null ? null : insurancePrice.setupItemName()
        );
        String itemCode = firstNonBlank(
                cashPrice.setupItemCode(),
                insurancePrice == null ? null : insurancePrice.setupItemCode()
        );

        return new InsurancePriceListCoverageCheckResult(
                true,
                false,
                true,
                notCoveredReason,
                billingItemType,
                sourceId,
                itemName,
                itemCode,
                cashPrice.unitPrice(),
                cashPrice.currency() != null ? cashPrice.currency() : effectiveCurrency,
                patientInsuranceId,
                warningMessageFor(notCoveredReason)
        );
    }

    private PatientInsurance loadInsurance(Long patientInsuranceId) {
        if (patientInsuranceId == null) {
            return null;
        }

        return patientInsuranceRepository.findById(patientInsuranceId).orElse(null);
    }

    private String resolveNotCoveredReason(InsurancePriceListCoverageCheckResult check) {
        if (check == null || check.notCoveredReason() == null || check.notCoveredReason().isBlank()) {
            return NOT_IN_INSURANCE_PRICE_LIST;
        }

        return check.notCoveredReason();
    }

    private InsurancePriceListCoverageCheckResult coveredResult(
            BillingItemTypes billingItemType,
            Long sourceId,
            Currency currency,
            Long patientInsuranceId
    ) {
        return new InsurancePriceListCoverageCheckResult(
                false,
                true,
                false,
                null,
                billingItemType,
                sourceId,
                null,
                null,
                null,
                currency,
                patientInsuranceId,
                null
        );
    }

    private Long resolveSourceId(
            BillingItemTypes billingItemType,
            Long serviceId,
            Long procedureId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        if (billingItemType == null) {
            return firstNonNull(serviceId, procedureId, diagnosticTestId, brandMedicationId);
        }

        return switch (billingItemType) {
            case SERVICE -> serviceId;
            case PROCEDURE -> procedureId;
            case MEDICATION -> brandMedicationId;
            case LABORATORY, RADIOLOGY, PATHOLOGY -> diagnosticTestId;
        };
    }

    private Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
