package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsibilityRole;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingResponsibilityService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingResponsibilityService.class
            );

    private static final String ENTITY_NAME =
            "billingChargeResponsibility";

    private static final int MONEY_SCALE = 4;

    private static final int PERCENTAGE_SCALE = 6;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final PatientInsuranceRepository
            patientInsuranceRepository;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final InsurancePatientShareCalculator
            insurancePatientShareCalculator;

    private final ServiceClient serviceClient;

    private final PreAuthorizationResolutionService preAuthorizationResolutionService;

    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final BillingChargeService billingChargeService;

    /**
     * Calculates and persists responsibility rows.
     *
     * Rules:
     * 1. Full exemption:
     *    Patient = 0
     *    Insurance = 0
     *
     * 2. Cash patient:
     *    Patient = full net amount
     *
     * 3. Insurance patient:
     *    Patient = configured patient share percentage
     *    Insurance = remaining amount
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void calculate(
            BillingProcessingContext context
    ) {
        validateContext(context);

        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        BillingChargeLine chargeLine =
                context.getChargeLine();

        PriceCalculationResult pricingResult =
                context.getPricingResult();

        BigDecimal netAmount =
                money(pricingResult.netAmount());

        LOG.debug(
                "[CALCULATE] Responsibility start "
                        + "pspId={} chargeLineId={} netAmount={} "
                        + "insuranceId={} exempted={}",
                item.getId(),
                chargeLine.getId(),
                netAmount,
                item.getPatientInsuranceId(),
                item.getIsExempted()
        );

        if (!item.isUncoveredCashItem()
                && (item.getPatientInsuranceId() != null
                || encounterInsuranceEligibilityService
                        .shouldEvaluatePreAuthorization(item.getEncounterId()))) {
            preAuthorizationResolutionService.refreshForBillingItem(item);
            patientServiceAndProductRepository.saveAndFlush(item);

            LOG.info(
                    "[CALCULATE] Pre-authorization refreshed for billing item "
                            + "pspId={} required={} status={} paymentStatus={}",
                    item.getId(),
                    item.getPreAuthorizationRequired(),
                    item.getPreAuthorizationStatus(),
                    item.getPaymentStatus()
            );
        }

        if (chargeLine.getId() != null) {
            supersedeActiveResponsibilities(
                    context,
                    "Responsibility recalculated"
            );
        }

        if (Boolean.TRUE.equals(item.getIsExempted())
                || netAmount.signum() == 0) {

            applyFullExemption(
                    context,
                    item,
                    chargeLine
            );

            return;
        }

        if (item.getPatientInsuranceId() == null || item.isUncoveredCashItem()) {
            applyCashResponsibility(
                    context,
                    item,
                    chargeLine,
                    netAmount
            );

            return;
        }

        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.REJECTED) {
            applyCashResponsibility(
                    context,
                    item,
                    chargeLine,
                    netAmount
            );

            return;
        }

        PatientInsurance insurance =
                loadAndValidateInsurance(item);

        if (!insurancePatientShareCalculator.isLatestCoverageInForce(insurance)) {
            LOG.warn(
                    "[CALCULATE] Eligibility is not in-force; billing as cash "
                            + "pspId={} patientInsuranceId={} netAmount={}",
                    item.getId(),
                    insurance.getId(),
                    netAmount
            );

            applyCashResponsibility(
                    context,
                    item,
                    chargeLine,
                    netAmount
            );

            return;
        }

        applyInsuranceResponsibility(
                context,
                item,
                chargeLine,
                insurance,
                netAmount
        );
    }

    private void applyFullExemption(
            BillingProcessingContext context,
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine
    ) {
        chargeLine.setPatientResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setInsuranceResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setOutstandingAmount(
                BigDecimal.ZERO
        );

        chargeLine.setStatus(
                BillingChargeLineStatus.DRAFT
        );

        billingChargeLineRepository.save(chargeLine);

        item.setPatientShareAmount(
                BigDecimal.ZERO
        );

        item.setInsuranceShareAmount(
                BigDecimal.ZERO
        );

        item.setPaidAmount(
                BigDecimal.ZERO
        );

        item.setRemainingAmount(
                BigDecimal.ZERO
        );

        item.setPaymentStatus(
                PaymentStatus.EXEMPTED
        );

        patientServiceAndProductRepository.save(item);

        context.setPatientResponsibility(null);
        context.setInsuranceResponsibility(null);
        context.setOtherPayerResponsibilityAmount(null);

        context.setPatientResponsibilityAmount(
                BigDecimal.ZERO
        );

        context.setInsuranceResponsibilityAmount(
                BigDecimal.ZERO
        );

        context.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        LOG.info(
                "[CALCULATE] Full exemption applied "
                        + "pspId={} chargeLineId={}",
                item.getId(),
                chargeLine.getId()
        );
    }

    private void applyCashResponsibility(
            BillingProcessingContext context,
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine,
            BigDecimal netAmount
    ) {
        String idempotencyKey =
                context.getIdempotencyKey()
                        + ":RESPONSIBILITY:PATIENT";

        BillingChargeResponsibility patientResponsibility =
                createResponsibility(
                        context,

                        ResponsiblePartyType.PATIENT,

                        ResponsibilityRole.PRIMARY,

                        null,

                        netAmount,

                        BigDecimal.ZERO,

                        BigDecimal.ZERO,

                        BigDecimal.ZERO,

                        BigDecimal.ZERO,

                        netAmount,

                        BigDecimal.ZERO,

                        idempotencyKey
                );

        chargeLine.setPatientResponsibilityAmount(
                netAmount
        );

        chargeLine.setInsuranceResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setOutstandingAmount(
                netAmount
        );

        chargeLine.setStatus(
                BillingChargeLineStatus.OPEN
        );
        billingChargeLineRepository.save(chargeLine);

        item.setPatientShareAmount(netAmount);

        item.setInsuranceShareAmount(
                BigDecimal.ZERO
        );

        item.setPaidAmount(
                BigDecimal.ZERO
        );

        item.setRemainingAmount(netAmount);

        item.setPaymentStatus(
                preAuthorizationResolutionService.resolveBillingPaymentStatus(item)
        );

        patientServiceAndProductRepository.save(item);

        context.setPatientResponsibility(
                patientResponsibility
        );

        context.setInsuranceResponsibility(null);
        context.setOtherPayerResponsibilityAmount(null);

        context.setPatientResponsibilityAmount(
                netAmount
        );

        context.setInsuranceResponsibilityAmount(
                BigDecimal.ZERO
        );

        context.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        LOG.info(
                "[CALCULATE] Cash responsibility created "
                        + "pspId={} chargeLineId={} amount={}",
                item.getId(),
                chargeLine.getId(),
                netAmount
        );
    }

    private void applyInsuranceResponsibility(
            BillingProcessingContext context,
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine,
            PatientInsurance insurance,
            BigDecimal netAmount
    ) {
        BigDecimal patientAmount =
                calculatePatientInsuranceShare(
                        context,
                        insurance,
                        item,
                        netAmount
                );

        BigDecimal insuranceAmount =
                money(
                        netAmount.subtract(
                                patientAmount
                        )
                );

        validateResponsibilityBalance(
                netAmount,
                patientAmount,
                insuranceAmount,
                BigDecimal.ZERO
        );

        BillingChargeResponsibility
                patientResponsibility = null;

        BillingChargeResponsibility
                insuranceResponsibility = null;

        if (patientAmount.signum() > 0) {
            patientResponsibility =
                    createResponsibility(
                            context,

                            ResponsiblePartyType.PATIENT,

                            ResponsibilityRole.PRIMARY,

                            insurance,

                            patientAmount,

                            calculatePercentage(
                                    patientAmount,
                                    netAmount
                            ),

                            BigDecimal.ZERO,

                            patientAmount,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            context.getIdempotencyKey()
                                    + ":RESPONSIBILITY:PATIENT"
                    );
        }

        if (insuranceAmount.signum() > 0) {
            insuranceResponsibility =
                    createResponsibility(
                            context,

                            ResponsiblePartyType.INSURANCE,

                            ResponsibilityRole.PRIMARY,

                            insurance,

                            insuranceAmount,

                            calculatePercentage(
                                    insuranceAmount,
                                    netAmount
                            ),

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            context.getIdempotencyKey()
                                    + ":RESPONSIBILITY:INSURANCE"
                    );
        }

        chargeLine.setPatientInsurance(
                insurance
        );

        chargeLine.setPatientResponsibilityAmount(
                patientAmount
        );

        chargeLine.setInsuranceResponsibilityAmount(
                insuranceAmount
        );

        chargeLine.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        chargeLine.setOutstandingAmount(
                netAmount
        );

        chargeLine.setStatus(
                BillingChargeLineStatus.OPEN
        );
        billingChargeLineRepository.save(chargeLine);

        item.setPatientShareAmount(
                patientAmount
        );

        item.setInsuranceShareAmount(
                insuranceAmount
        );

        item.setPaidAmount(
                BigDecimal.ZERO
        );

        item.setRemainingAmount(
                patientAmount
        );

        item.setPaymentStatus(
                preAuthorizationResolutionService.resolveBillingPaymentStatus(item)
        );

        patientServiceAndProductRepository.save(item);

        context.setPatientResponsibility(
                patientResponsibility
        );

        context.setInsuranceResponsibility(
                insuranceResponsibility
        );

        context.setOtherPayerResponsibilityAmount(null);

        context.setPatientResponsibilityAmount(
                patientAmount
        );

        context.setInsuranceResponsibilityAmount(
                insuranceAmount
        );

        context.setOtherPayerResponsibilityAmount(
                BigDecimal.ZERO
        );

        LOG.info(
                "[CALCULATE] Insurance responsibility created "
                        + "pspId={} chargeLineId={} "
                        + "patientAmount={} insuranceAmount={} "
                        + "patientInsuranceId={}",
                item.getId(),
                chargeLine.getId(),
                patientAmount,
                insuranceAmount,
                insurance.getId()
        );
    }

    private BillingChargeResponsibility createResponsibility(
            BillingProcessingContext context,
            ResponsiblePartyType partyType,
            ResponsibilityRole responsibilityRole,
            PatientInsurance insurance,
            BigDecimal responsibilityAmount,
            BigDecimal coveragePercentage,
            BigDecimal deductibleAmount,
            BigDecimal copayAmount,
            BigDecimal coinsuranceAmount,
            BigDecimal nonCoveredAmount,
            BigDecimal contractualAdjustmentAmount,
            String idempotencyKey
    ) {
        BillingChargeResponsibility existing =
                billingChargeResponsibilityRepository
                        .findByIdempotencyKey(
                                idempotencyKey
                        )
                        .orElse(null);

        if (existing != null
                && isActiveResponsibility(existing.getStatus())) {
            LOG.debug(
                    "[CREATE] Existing responsibility returned "
                            + "responsibilityId={} idempotencyKey={}",
                    existing.getId(),
                    idempotencyKey
            );

            return existing;
        }

        BillingChargeLine chargeLine =
                context.getChargeLine();

        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        BigDecimal amount =
                money(responsibilityAmount);

        BillingChargeResponsibility responsibility =
                BillingChargeResponsibility.builder()
                        .charge(
                                chargeLine.getCharge()
                        )
                        .chargeLine(chargeLine)
                        .patientServiceProduct(item)
                        .patient(
                                chargeLine.getPatient()
                        )
                        .encounter(
                                chargeLine.getEncounter()
                        )

                        .responsiblePartyType(
                                partyType
                        )

                        .responsibilityRole(
                                responsibilityRole
                        )

                        .payerId(null)

                        .patientInsurance(
                                insurance
                        )

                        .policyNumber(
                                insurance == null
                                        ? null
                                        : insurance.getPolicyNumber()
                        )

                        .memberNumber(
                                resolveMemberNumber(
                                        insurance
                                )
                        )

                        .responsibilityAmount(
                                amount
                        )

                        .allocatedAmount(
                                BigDecimal.ZERO
                        )

                        .outstandingAmount(
                                amount
                        )

                        .coveragePercentage(
                                percentage(
                                        coveragePercentage
                                )
                        )

                        .deductibleAmount(
                                money(deductibleAmount)
                        )

                        .copayAmount(
                                money(copayAmount)
                        )

                        .coinsuranceAmount(
                                money(coinsuranceAmount)
                        )

                        .nonCoveredAmount(
                                money(nonCoveredAmount)
                        )

                        .contractualAdjustmentAmount(
                                money(
                                        contractualAdjustmentAmount
                                )
                        )

                        .currency(
                                chargeLine.getCurrency()
                        )

                        .status(
                                BillingResponsibilityStatus.CALCULATED
                        )

                        .preAuthorizationRequired(
                                Boolean.TRUE.equals(
                                        item.getPreAuthorizationRequired()
                                )
                        )

                        .preAuthorizationStatus(item.getPreAuthorizationStatus())

                        .preAuthorizationReferenceNo(
                                item.getPreAuthorizationReferenceNo()
                        )

                        .claimId(null)
                        .claimReference(null)

                        .supersededResponsibility(
                                null
                        )

                        .adjustmentReason(null)

                        .effectiveDate(
                                Instant.now()
                        )

                        .closedDate(null)

                        .idempotencyKey(
                                idempotencyKey
                        )

                        .build();

        try {
            BillingChargeResponsibility saved =
                    billingChargeResponsibilityRepository
                            .saveAndFlush(
                                    responsibility
                            );

            LOG.info(
                    "[CREATE] Responsibility created "
                            + "responsibilityId={} chargeLineId={} "
                            + "partyType={} role={} amount={}",
                    saved.getId(),
                    chargeLine.getId(),
                    partyType,
                    responsibilityRole,
                    amount
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            LOG.error(
                    "[CREATE] Responsibility creation failed "
                            + "chargeLineId={} partyType={} "
                            + "role={} amount={}",
                    chargeLine.getId(),
                    partyType,
                    responsibilityRole,
                    amount,
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to create billing responsibility.",
                    ENTITY_NAME,
                    "responsibility.create.failed"
            );
        }
    }

    private BigDecimal calculatePatientInsuranceShare(
            BillingProcessingContext context,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount
    ) {
        InsuranceSplit split =
                insurancePatientShareCalculator.calculateSplit(
                        insurance,
                        item,
                        netAmount,
                        context.getVisitMaxLimitTracker()
                );

        return money(split.patientShare());
    }

    private String resolveServiceCategory(
            PatientServiceAndProduct item
    ) {
        if (item == null
                || item.getBillingItemType() != BillingItemTypes.SERVICE
                || item.getServiceId() == null) {
            return null;
        }

        try {
            ServiceSetupDTO service =
                    serviceClient.getServiceDetails(
                            item.getServiceId()
                    );

            return service == null
                    ? null
                    : service.category();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[INSURANCE] Unable to resolve service category serviceId={}",
                    item.getServiceId(),
                    exception
            );
            return null;
        }
    }

    private PatientInsurance loadAndValidateInsurance(
            PatientServiceAndProduct item
    ) {
        PatientInsurance insurance =
                patientInsuranceRepository
                        .findByIdAndPatient_Id(
                                item.getPatientInsuranceId(),
                                item.getPatientId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient insurance was not found for this patient.",
                                        ENTITY_NAME,
                                        "patientInsurance.notfound"
                                )
                        );

        if (insurance.getExpirationDate() == null
                || insurance.getExpirationDate().isBefore(LocalDate.now())) {
            throw new BadRequestAlertException(
                    "Patient insurance is expired or has no expiration date.",
                    ENTITY_NAME,
                    "patientInsurance.expired"
            );
        }

        if (insurance.getPayerNphiesId() == null
                || insurance.getPayerNphiesId().isBlank()) {
            throw new BadRequestAlertException(
                    "Waseel payer NPHIES ID is required.",
                    ENTITY_NAME,
                    "patientInsurance.payerNphiesId.required"
            );
        }

        if (insurance.getMemberCardId() == null
                || insurance.getMemberCardId().isBlank()) {
            throw new BadRequestAlertException(
                    "Waseel member card ID is required.",
                    ENTITY_NAME,
                    "patientInsurance.memberCardId.required"
            );
        }

        return insurance;
    }

    private String resolveMemberNumber(
            PatientInsurance insurance
    ) {
        if (insurance == null) {
            return null;
        }

        if (insurance.getMemberCardId() != null
                && !insurance.getMemberCardId().isBlank()) {
            return insurance.getMemberCardId().trim();
        }

        return insurance.getPolicyNumber();
    }

    private BigDecimal calculatePercentage(
            BigDecimal amount,
            BigDecimal total
    ) {
        BigDecimal normalizedAmount =
                money(amount);

        BigDecimal normalizedTotal =
                money(total);

        if (normalizedTotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(
                    PERCENTAGE_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return normalizedAmount
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        normalizedTotal,
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private void validateResponsibilityBalance(
            BigDecimal netAmount,
            BigDecimal patientAmount,
            BigDecimal insuranceAmount,
            BigDecimal otherPayerAmount
    ) {
        BigDecimal responsibilityTotal =
                money(patientAmount)
                        .add(
                                money(
                                        insuranceAmount
                                )
                        )
                        .add(
                                money(
                                        otherPayerAmount
                                )
                        );

        if (responsibilityTotal.compareTo(
                money(netAmount)
        ) != 0) {
            throw new BadRequestAlertException(
                    "Responsibility total must equal net amount.",
                    ENTITY_NAME,
                    "responsibility.balance.invalid"
            );
        }
    }

    private void validateContext(
            BillingProcessingContext context
    ) {
        if (context == null) {
            throw new BadRequestAlertException(
                    "Billing processing context is required.",
                    ENTITY_NAME,
                    "context.required"
            );
        }

        if (context.getPatientServiceProduct()
                == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.required"
            );
        }

        if (context.getChargeLine() == null
                || context.getChargeLine().getId()
                == null) {
            throw new BadRequestAlertException(
                    "Persisted billing charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
            );
        }

        if (context.getPricingResult()
                == null) {
            throw new BadRequestAlertException(
                    "Pricing result is required.",
                    ENTITY_NAME,
                    "pricingResult.required"
            );
        }

        if (context.getIdempotencyKey() == null
                || context.getIdempotencyKey()
                .isBlank()) {
            throw new BadRequestAlertException(
                    "Idempotency key is required.",
                    ENTITY_NAME,
                    "idempotencyKey.required"
            );
        }
    }

    private BigDecimal percentage(
            BigDecimal value
    ) {
        BigDecimal result =
                defaultZero(value)
                        .setScale(
                                PERCENTAGE_SCALE,
                                RoundingMode.HALF_UP
                        );

        if (result.signum() < 0
                || result.compareTo(
                BigDecimal.valueOf(100)
        ) > 0) {
            throw new BadRequestAlertException(
                    "Percentage must be between 0 and 100.",
                    ENTITY_NAME,
                    "percentage.invalid"
            );
        }

        return result;
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return defaultZero(value)
                .setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void loadPatientResponsibility(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getChargeLine() == null
                || context.getChargeLine().getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
            );
        }

        BillingChargeResponsibility responsibility =
                billingChargeResponsibilityRepository
                        .findFirstByChargeLine_IdAndResponsiblePartyTypeAndStatusOrderByIdDesc(
                                context.getChargeLine().getId(),
                                ResponsiblePartyType.PATIENT,
                                BillingResponsibilityStatus.CALCULATED
                        )
                        .orElse(null);

        context.setPatientResponsibility(
                responsibility
        );

        context.setPatientResponsibilityAmount(
                responsibility == null
                        ? BigDecimal.ZERO
                        : money(
                        responsibility
                                .getResponsibilityAmount()
                )
        );
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void cancelResponsibilitiesForChargeLine(
            Long chargeLineId,
            String reason
    ) {
        if (chargeLineId == null) {
            throw new BadRequestAlertException(
                    "Charge-line ID is required.",
                    ENTITY_NAME,
                    "chargeLineId.required"
            );
        }

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByChargeLine_IdOrderByIdAsc(
                                chargeLineId
                        );

        for (BillingChargeResponsibility responsibility
                : responsibilities) {

            BigDecimal allocated =
                    money(
                            responsibility.getAllocatedAmount()
                    );

            if (allocated.signum() > 0) {
                throw new BadRequestAlertException(
                        "Responsibility still has allocated amount. "
                                + "Reverse allocations before cancellation.",
                        ENTITY_NAME,
                        "responsibility.hasAllocation"
                );
            }

            closeResponsibilityForReplacement(
                    responsibility,
                    BillingResponsibilityStatus.CANCELLED,
                    reason
            );
        }

        billingChargeResponsibilityRepository
                .saveAll(responsibilities);
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void supersedeActiveResponsibilities(
            BillingProcessingContext context,
            String reason
    ) {
        if (context == null
                || context.getChargeLine() == null
                || context.getChargeLine().getId() == null) {

            throw new BadRequestAlertException(
                    "Persisted charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
            );
        }

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByChargeLine_IdAndStatusNotInOrderByIdAsc(
                                context.getChargeLine().getId(),
                                List.of(
                                        BillingResponsibilityStatus.CANCELLED,
                                        BillingResponsibilityStatus.SUPERSEDED
                                )
                        );

        for (BillingChargeResponsibility responsibility
                : responsibilities) {

            if (money(
                    responsibility.getAllocatedAmount()
            ).signum() > 0) {
                throw new BadRequestAlertException(
                        "Responsibility has an allocated amount. "
                                + "Reverse allocations before repricing.",
                        ENTITY_NAME,
                        "responsibility.hasAllocation"
                );
            }

            closeResponsibilityForReplacement(
                    responsibility,
                    BillingResponsibilityStatus.SUPERSEDED,
                    reason
            );
        }

        billingChargeResponsibilityRepository.saveAll(
                responsibilities
        );

        context.setPatientResponsibility(null);
        context.setInsuranceResponsibility(null);
    }

    /**
     * Recalculates patient/insurance shares on open charge lines after benefit rules change
     * (e.g. eligibility re-check). Skips lines that already have allocations.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public int refreshInsuranceResponsibilitiesForEncounter(
            Long encounterId
    ) {
        if (encounterId == null) {
            return 0;
        }

        List<BillingChargeLine> chargeLines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                List.of(
                                        BillingChargeLineStatus.CANCELLED,
                                        BillingChargeLineStatus.REVERSED
                                )
                        );

        int refreshedCount = 0;
        Set<BillingCharge> chargesToRecalculate = new HashSet<>();
        VisitMaxLimitTracker visitMaxLimitTracker = null;
        Long trackerInsuranceId = null;
        BigDecimal lockedVisitPatientShare = BigDecimal.ZERO;

        for (BillingChargeLine chargeLine : chargeLines) {
            PatientServiceAndProduct lockedItem =
                    chargeLine == null ? null : chargeLine.getPatientServiceProduct();
            if (lockedItem != null
                    && lockedItem.getPatientInsuranceId() != null
                    && !Boolean.TRUE.equals(lockedItem.getIsExempted())
                    && !lockedItem.isUncoveredCashItem()
                    && (chargeLine.getStatus() != BillingChargeLineStatus.OPEN
                    || money(chargeLine.getAllocatedAmount()).signum() > 0
                    || money(chargeLine.getReservedAmount()).signum() > 0)) {
                lockedVisitPatientShare =
                        lockedVisitPatientShare.add(
                                money(chargeLine.getPatientResponsibilityAmount())
                        );
            }
        }

        for (BillingChargeLine chargeLine : chargeLines) {
            if (chargeLine == null
                    || chargeLine.getId() == null
                    || chargeLine.getStatus() != BillingChargeLineStatus.OPEN) {
                continue;
            }

            if (money(chargeLine.getAllocatedAmount()).signum() > 0
                    || money(chargeLine.getReservedAmount()).signum() > 0) {
                continue;
            }

            PatientServiceAndProduct item =
                    chargeLine.getPatientServiceProduct();

            if (item == null
                    || item.getPatientInsuranceId() == null
                    || Boolean.TRUE.equals(item.getIsExempted())) {
                continue;
            }

            BillingProcessingContext context =
                    buildRefreshContext(chargeLine, item);

            PatientInsurance insurance =
                    patientInsuranceRepository
                            .findById(item.getPatientInsuranceId())
                            .orElse(null);
            if (insurance != null) {
                if (visitMaxLimitTracker == null
                        || !insurance.getId().equals(trackerInsuranceId)) {
                    visitMaxLimitTracker =
                            insurancePatientShareCalculator
                                    .createVisitMaxLimitTracker(
                                            insurance,
                                            lockedVisitPatientShare
                                    );
                    trackerInsuranceId = insurance.getId();
                }
                context.setVisitMaxLimitTracker(visitMaxLimitTracker);
            }

            try {
                supersedeActiveResponsibilities(
                        context,
                        "Benefit rules refreshed after eligibility sync"
                );
                calculate(context);
                refreshedCount++;

                if (chargeLine.getCharge() != null) {
                    chargesToRecalculate.add(chargeLine.getCharge());
                }

                LOG.info(
                        "[REFRESH] Recalculated insurance responsibility "
                                + "encounterId={} pspId={} chargeLineId={} "
                                + "patient={} insurance={}",
                        encounterId,
                        item.getId(),
                        chargeLine.getId(),
                        chargeLine.getPatientResponsibilityAmount(),
                        chargeLine.getInsuranceResponsibilityAmount()
                );
            } catch (BadRequestAlertException exception) {
                LOG.warn(
                        "[REFRESH] Skipped charge line encounterId={} chargeLineId={} reason={}",
                        encounterId,
                        chargeLine.getId(),
                        exception.getMessage()
                );
            }
        }

        for (BillingCharge charge : chargesToRecalculate) {
            BillingProcessingContext chargeContext =
                    BillingProcessingContext.builder()
                            .charge(charge)
                            .build();

            billingChargeService.recalculateChargeTotals(chargeContext);
        }

        LOG.info(
                "[REFRESH] Encounter insurance responsibility refresh completed "
                        + "encounterId={} refreshedLines={}",
                encounterId,
                refreshedCount
        );

        return refreshedCount;
    }

    private BillingProcessingContext buildRefreshContext(
            BillingChargeLine chargeLine,
            PatientServiceAndProduct item
    ) {
        BigDecimal netAmount = money(chargeLine.getNetAmount());

        PriceCalculationResult pricingResult =
                new PriceCalculationResult(
                        money(chargeLine.getQuantity()),
                        money(chargeLine.getUnitPrice()),
                        money(chargeLine.getGrossAmount()),
                        money(chargeLine.getDiscountAmount()),
                        money(chargeLine.getExemptionAmount()),
                        BigDecimal.ZERO,
                        money(chargeLine.getTaxAmount()),
                        netAmount
                );

        return BillingProcessingContext.builder()
                .transactionGroupId(UUID.randomUUID())
                .idempotencyKey(
                        "ELIGIBILITY_REFRESH:"
                                + chargeLine.getId()
                                + ":"
                                + UUID.randomUUID()
                )
                .eventType(BillingEventType.MANUAL)
                .patientServiceProduct(item)
                .charge(chargeLine.getCharge())
                .chargeLine(chargeLine)
                .pricingResult(pricingResult)
                .build();
    }

    /**
     * Closes a responsibility row while keeping
     * {@code responsibility_amount = allocated_amount + outstanding_amount}.
     */
    private boolean isActiveResponsibility(
            BillingResponsibilityStatus status
    ) {
        return status != BillingResponsibilityStatus.CANCELLED
                && status != BillingResponsibilityStatus.SUPERSEDED;
    }

    private void closeResponsibilityForReplacement(
            BillingChargeResponsibility responsibility,
            BillingResponsibilityStatus status,
            String reason
    ) {
        BigDecimal allocated =
                money(responsibility.getAllocatedAmount());

        BigDecimal zeroMoney =
                BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal zeroPercentage =
                BigDecimal.ZERO.setScale(
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_UP
                );

        responsibility.setOutstandingAmount(zeroMoney);
        responsibility.setResponsibilityAmount(allocated);
        responsibility.setNonCoveredAmount(zeroMoney);
        responsibility.setCopayAmount(zeroMoney);
        responsibility.setCoinsuranceAmount(zeroMoney);
        responsibility.setDeductibleAmount(zeroMoney);
        responsibility.setContractualAdjustmentAmount(zeroMoney);
        responsibility.setCoveragePercentage(zeroPercentage);

        responsibility.setStatus(status);

        responsibility.setAdjustmentReason(reason);

        responsibility.setClosedDate(
                Instant.now()
        );
    }

}