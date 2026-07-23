package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsibilityRole;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
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

        if (Boolean.TRUE.equals(item.getIsExempted())
                || netAmount.signum() == 0) {

            applyFullExemption(
                    context,
                    item,
                    chargeLine
            );

            return;
        }

        if (item.getPatientInsuranceId() == null) {
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
                PaymentStatus.PENDING
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
                        insurance,
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

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            BigDecimal.ZERO,

                            patientAmount,

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
                netAmount
        );

        item.setPaymentStatus(
                PaymentStatus.PENDING
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

        if (existing != null) {
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

                        .payerId(
                                insurance == null
                                        ? null
                                        : insurance.getPayorId()
                        )

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

    /**
     * Current assumption:
     * PatientInsurance.patientShare is a percentage between 0 and 100.
     */
    private BigDecimal calculatePatientInsuranceShare(
            PatientInsurance insurance,
            BigDecimal netAmount
    ) {
        BigDecimal patientSharePercentage =
                percentage(
                        insurance.getPatientShare()
                );

        BigDecimal result =
                netAmount
                        .multiply(
                                patientSharePercentage
                        )
                        .divide(
                                BigDecimal.valueOf(100),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP
                        );

        if (result.compareTo(netAmount) > 0) {
            return netAmount;
        }

        return money(result);
    }

    private PatientInsurance loadAndValidateInsurance(
            PatientServiceAndProduct item
    ) {
        PatientInsurance insurance =
                patientInsuranceRepository
                        .findById(
                                item.getPatientInsuranceId()
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient insurance not found with id "
                                                + item.getPatientInsuranceId(),
                                        ENTITY_NAME,
                                        "patientInsurance.notfound"
                                )
                        );

        if (insurance.getPatient() == null
                || insurance.getPatient().getId() == null
                || !insurance.getPatient()
                .getId()
                .equals(item.getPatientId())) {

            throw new BadRequestAlertException(
                    "Patient insurance does not belong to the patient.",
                    ENTITY_NAME,
                    "patientInsurance.patient.mismatch"
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
}