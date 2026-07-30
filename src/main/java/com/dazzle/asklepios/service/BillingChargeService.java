package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsibilityRole;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class BillingChargeService {

    private static final Logger LOG =
            LoggerFactory.getLogger(BillingChargeService.class);

    private static final String ENTITY_NAME = "billingCharge";

    private static final EnumSet<BillingChargeStatus>
            ACTIVE_CHARGE_STATUSES = EnumSet.of(
            BillingChargeStatus.DRAFT,
            BillingChargeStatus.OPEN,
            BillingChargeStatus.PARTIALLY_ALLOCATED,
            BillingChargeStatus.FULLY_ALLOCATED
    );

    private static final EnumSet<BillingChargeStatus>
            EXCLUDED_CHARGE_STATUSES = EnumSet.of(
            BillingChargeStatus.CANCELLED,
            BillingChargeStatus.REVERSED
    );

    private static final EnumSet<BillingChargeLineStatus>
            EXCLUDED_LINE_STATUSES = EnumSet.of(
            BillingChargeLineStatus.CANCELLED,
            BillingChargeLineStatus.REVERSED
    );

    private static final int MONEY_SCALE = 4;

    private final BillingChargeRepository billingChargeRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    /**
     * Finds the active encounter charge or creates a new charge header.
     *
     * Must be called from BillingTransactionService.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingCharge createOrLoadCharge(
            BillingProcessingContext context
    ) {
        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        LOG.debug(
                "[CREATE_OR_LOAD] BillingCharge pspId={} patientId={} encounterId={} currency={}",
                item.getId(),
                item.getPatientId(),
                item.getEncounterId(),
                item.getCurrency()
        );

        validatePatientItem(item);

        BillingCharge existingCharge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndPatient_IdAndCurrencyAndStatusInOrderByIdAsc(
                                item.getEncounterId(),
                                item.getPatientId(),
                                item.getCurrency(),
                                ACTIVE_CHARGE_STATUSES
                        )
                        .orElse(null);

        if (existingCharge != null) {
            context.setCharge(existingCharge);

            LOG.debug(
                    "[CREATE_OR_LOAD] Existing BillingCharge found chargeId={} chargeNumber={}",
                    existingCharge.getId(),
                    existingCharge.getChargeNumber()
            );

            return existingCharge;
        }

        Patient patient = findPatient(item.getPatientId());

        PatientEncounter encounter =
                findEncounter(item.getEncounterId());

        validatePatientMatchesEncounter(
                patient,
                encounter
        );

        String idempotencyKey =
                context.getIdempotencyKey() + ":CHARGE";

        BillingCharge charge =
                BillingCharge.builder()
                        .chargeNumber(generateChargeNumber())
                        .patient(patient)
                        .encounter(encounter)
                        .chargeType(BillingChargeType.ENCOUNTER)
                        .currency(item.getCurrency())
                        .grossAmount(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .exemptionAmount(BigDecimal.ZERO)
                        .taxAmount(BigDecimal.ZERO)
                        .netAmount(BigDecimal.ZERO)
                        .allocatedAmount(BigDecimal.ZERO)
                        .outstandingAmount(BigDecimal.ZERO)
                        .lineCount(0)
                        .status(BillingChargeStatus.DRAFT)
                        .chargeDate(Instant.now())
                        .idempotencyKey(idempotencyKey)
                        .sourceType("ENCOUNTER")
                        .sourceId(item.getEncounterId())
                        .sourceReference(
                                "ENCOUNTER:" + item.getEncounterId()
                        )
                        .build();

        try {
            BillingCharge saved =
                    billingChargeRepository.saveAndFlush(charge);

            context.setCharge(saved);

            LOG.info(
                    "[CREATE_OR_LOAD] BillingCharge created chargeId={} chargeNumber={} encounterId={} patientId={}",
                    saved.getId(),
                    saved.getChargeNumber(),
                    item.getEncounterId(),
                    item.getPatientId()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            LOG.warn(
                    "[CREATE_OR_LOAD] BillingCharge constraint failure pspId={} encounterId={}",
                    item.getId(),
                    item.getEncounterId(),
                    exception
            );

            throw handleConstraintViolation(exception);
        }
    }

    /**
     * Creates the financial charge line from PatientServiceAndProduct.
     *
     * Pricing must already exist in context.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLine createChargeLine(
            BillingProcessingContext context
    ) {
        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        BillingCharge charge =
                requireCharge(context);

        PriceCalculationResult pricing =
                requirePricingResult(context);

        LOG.debug(
                "[CREATE_LINE] BillingChargeLine pspId={} chargeId={} netAmount={}",
                item.getId(),
                charge.getId(),
                pricing.netAmount()
        );

        BillingChargeLine existingLine =
                billingChargeLineRepository
                        .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                                item.getId(),
                                EXCLUDED_LINE_STATUSES
                        )
                        .orElse(null);

        if (existingLine != null) {
            context.setCharge(existingLine.getCharge());
            context.setChargeLine(existingLine);

            LOG.debug(
                    "[CREATE_LINE] Existing charge line found lineId={} pspId={}",
                    existingLine.getId(),
                    item.getId()
            );

            return existingLine;
        }

        PatientInsurance patientInsurance =
                loadPatientInsurance(item);

        BillingChargeLine line =
                BillingChargeLine.builder()
                        .charge(charge)
                        .patientServiceProduct(item)
                        .patient(charge.getPatient())
                        .encounter(charge.getEncounter())

                        /*
                         * BillingChargeLine and PatientServiceAndProduct
                         * must use the same BillingItemTypes enum.
                         */
                        .billingItemType(item.getBillingItemType())

                        .brandMedicationId(
                                item.getBrandMedicationId()
                        )
                        .diagnosticTestId(
                                item.getDiagnosticTestId()
                        )
                        .serviceId(item.getServiceId())
                        .procedureId(item.getProcedureId())

                        .itemCode(resolveItemCode(context))
                        .itemDescription(
                                resolveItemDescription(context)
                        )

                        .serviceSource(item.getServiceSource())
                        .sourceId(resolveSourceId(item))

                        .quantity(pricing.quantity())
                        .unitPrice(pricing.unitPrice())
                        .grossAmount(pricing.grossAmount())
                        .discountAmount(
                                pricing.discountAmount()
                        )
                        .exemptionAmount(
                                pricing.exemptionAmount()
                        )
                        .taxAmount(pricing.taxAmount())
                        .netAmount(pricing.netAmount())

                        .patientResponsibilityAmount(
                                BigDecimal.ZERO
                        )
                        .insuranceResponsibilityAmount(
                                BigDecimal.ZERO
                        )
                        .otherPayerResponsibilityAmount(
                                BigDecimal.ZERO
                        )
                        .allocatedAmount(BigDecimal.ZERO)
                        .outstandingAmount(
                                pricing.netAmount()
                        )
                        .reservedAmount(BigDecimal.ZERO)

                        .currency(item.getCurrency())
                        .status(BillingChargeLineStatus.DRAFT)

                        .patientInsurance(patientInsurance)

                        .preAuthorizationRequired(
                                Boolean.TRUE.equals(
                                        item.getPreAuthorizationRequired()
                                )
                        )
                        .preAuthorizationStatus(
                                item.getPreAuthorizationStatus()
                        )
                        .preAuthorizationRequestId(
                                item.getPreAuthorizationRequestId()
                        )
                        .preAuthorizationReferenceNo(
                                item.getPreAuthorizationReferenceNo()
                        )

                        .idempotencyKey(
                                context.getIdempotencyKey()
                                        + ":CHARGE_LINE"
                        )
                        .build();

        try {
            BillingChargeLine saved =
                    billingChargeLineRepository.saveAndFlush(line);

            context.setChargeLine(saved);

            updateOperationalItemAfterPricing(
                    item,
                    pricing
            );

            LOG.info(
                    "[CREATE_LINE] BillingChargeLine created lineId={} chargeId={} pspId={} gross={} exemption={} net={}",
                    saved.getId(),
                    charge.getId(),
                    item.getId(),
                    saved.getGrossAmount(),
                    saved.getExemptionAmount(),
                    saved.getNetAmount()
            );

            return saved;

        } catch (DataIntegrityViolationException
                 | JpaSystemException exception) {

            LOG.warn(
                    "[CREATE_LINE] BillingChargeLine constraint failure chargeId={} pspId={}",
                    charge.getId(),
                    item.getId(),
                    exception
            );

            throw handleConstraintViolation(exception);
        }
    }

    /**
     * Loads and locks the active charge line.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLine loadExistingChargeLine(
            BillingProcessingContext context
    ) {
        return loadExistingChargeLine(
                context,
                null
        );
    }

    /**
     * Loads and locks the active charge line, optionally scoped to an encounter.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLine loadExistingChargeLine(
            BillingProcessingContext context,
            Long encounterId
    ) {
        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        BillingChargeLine line =
                findActiveChargeLine(
                        item.getId(),
                        encounterId
                )
                        .orElseThrow(() -> {
                            LOG.warn(
                                    "[LOAD_LINE] Active charge line not found pspId={} encounterId={}",
                                    item.getId(),
                                    encounterId
                            );

                            return new NotFoundAlertException(
                                    "Active billing charge line not found for patient service/product "
                                            + item.getId(),
                                    ENTITY_NAME,
                                    "chargeLine.notfound"
                            );
                        });

        context.setCharge(line.getCharge());
        context.setChargeLine(line);

        return line;
    }

    /**
     * Finds the active charge line for a PSP, preferring an encounter-scoped match.
     */
    public java.util.Optional<BillingChargeLine> findActiveChargeLine(
            Long patientServiceProductId,
            Long encounterId
    ) {
        if (encounterId != null) {
            java.util.Optional<BillingChargeLine> encounterLine =
                    billingChargeLineRepository
                            .findFirstByEncounter_IdAndPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                                    encounterId,
                                    patientServiceProductId,
                                    EXCLUDED_LINE_STATUSES
                            );

            if (encounterLine.isPresent()) {
                return encounterLine;
            }
        }

        return billingChargeLineRepository
                .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                        patientServiceProductId,
                        EXCLUDED_LINE_STATUSES
                );
    }

    /**
     * Applies newly calculated price values to an existing line.
     *
     * Active allocations must not exceed the new net amount.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLine applyPricingToExistingChargeLine(
            BillingProcessingContext context
    ) {
        BillingChargeLine contextLine =
                requireChargeLine(context);

        PriceCalculationResult pricing =
                requirePricingResult(context);

        BillingChargeLine line =
                billingChargeLineRepository
                        .findById(contextLine.getId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing charge line not found with id "
                                                + contextLine.getId(),
                                        ENTITY_NAME,
                                        "chargeLine.notfound"
                                )
                        );

        BigDecimal allocatedAmount =
                defaultZero(line.getAllocatedAmount());

        BigDecimal newOutstandingAmount =
                pricing.netAmount()
                        .subtract(allocatedAmount);

        if (newOutstandingAmount.signum() < 0) {
            throw new BadRequestAlertException(
                    "New net amount is less than the already allocated amount. Reverse or adjust allocations first.",
                    ENTITY_NAME,
                    "pricing.lessThanAllocated"
            );
        }

        line.setQuantity(pricing.quantity());
        line.setUnitPrice(pricing.unitPrice());
        line.setGrossAmount(pricing.grossAmount());
        line.setDiscountAmount(
                pricing.discountAmount()
        );
        line.setExemptionAmount(
                pricing.exemptionAmount()
        );
        line.setTaxAmount(pricing.taxAmount());
        line.setNetAmount(pricing.netAmount());
        line.setOutstandingAmount(
                newOutstandingAmount
        );

        if (Boolean.TRUE.equals(
                line.getPatientServiceProduct()
                        .getIsExempted()
        )) {
            line.setPatientResponsibilityAmount(
                    BigDecimal.ZERO
            );
            line.setInsuranceResponsibilityAmount(
                    BigDecimal.ZERO
            );
            line.setOtherPayerResponsibilityAmount(
                    BigDecimal.ZERO
            );
            line.setOutstandingAmount(
                    BigDecimal.ZERO
            );
            line.setReservedAmount(
                    BigDecimal.ZERO
            );
        }

        BillingChargeLine saved =
                billingChargeLineRepository.save(line);

        updateOperationalItemAfterPricing(
                saved.getPatientServiceProduct(),
                pricing
        );

        context.setCharge(saved.getCharge());
        context.setChargeLine(saved);

        LOG.info(
                "[REPRICE_LINE] BillingChargeLine updated lineId={} pspId={} gross={} exemption={} net={}",
                saved.getId(),
                saved.getPatientServiceProduct().getId(),
                saved.getGrossAmount(),
                saved.getExemptionAmount(),
                saved.getNetAmount()
        );

        return saved;
    }

    /**
     * Recalculates charge header totals from active lines.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingCharge recalculateChargeTotals(
            BillingProcessingContext context
    ) {
        BillingCharge contextCharge =
                requireCharge(context);

        BillingCharge charge =
                billingChargeRepository
                        .findById(contextCharge.getId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing charge not found with id "
                                                + contextCharge.getId(),
                                        ENTITY_NAME,
                                        "charge.notfound"
                                )
                        );

        List<BillingChargeLine> activeLines =
                billingChargeLineRepository
                        .findAllByCharge_IdAndStatusNotInOrderByIdAsc(
                                charge.getId(),
                                EXCLUDED_LINE_STATUSES
                        );

        BigDecimal grossAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getGrossAmount
                );

        BigDecimal discountAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getDiscountAmount
                );

        BigDecimal exemptionAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getExemptionAmount
                );

        BigDecimal taxAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getTaxAmount
                );

        BigDecimal netAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getNetAmount
                );

        BigDecimal allocatedAmount =
                sum(
                        activeLines,
                        BillingChargeLine::getAllocatedAmount
                );

        BigDecimal outstandingAmount =
                netAmount.subtract(allocatedAmount);

        if (outstandingAmount.signum() < 0) {
            throw new BadRequestAlertException(
                    "Charge allocated amount exceeds charge net amount.",
                    ENTITY_NAME,
                    "allocated.exceedsNet"
            );
        }

        charge.setGrossAmount(grossAmount);
        charge.setDiscountAmount(discountAmount);
        charge.setExemptionAmount(exemptionAmount);
        charge.setTaxAmount(taxAmount);
        charge.setNetAmount(netAmount);
        charge.setAllocatedAmount(allocatedAmount);
        charge.setOutstandingAmount(
                outstandingAmount
        );
        charge.setLineCount(activeLines.size());

        for (BillingChargeLine line : activeLines) {
            line.setStatus(
                    determineLineStatus(
                            line,
                            charge.getStatus()
                    )
            );
            billingChargeLineRepository.save(line);
        }

        charge.setStatus(
                determineChargeStatus(
                        charge.getStatus(),
                        activeLines.size(),
                        netAmount,
                        allocatedAmount,
                        outstandingAmount
                )
        );

        BillingCharge saved =
                billingChargeRepository.save(charge);

        context.setCharge(saved);

        LOG.info(
                "[RECALCULATE] BillingCharge totals updated chargeId={} gross={} discount={} exemption={} tax={} net={} allocated={} outstanding={}",
                saved.getId(),
                grossAmount,
                discountAmount,
                exemptionAmount,
                taxAmount,
                netAmount,
                allocatedAmount,
                outstandingAmount
        );

        return saved;
    }

    /**
     * Creates a post-invoice debit-note charge line using the supplied manual
     * price. The line is recorded on the existing encounter charge (including
     * CLOSED charges) and remains collectable until payment is collected.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLine createDebitNoteAdjustmentChargeLine(
            PatientServiceAndProduct item,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal patientShareAmount,
            BigDecimal insuranceShareAmount,
            String idempotencyKeyPrefix
    ) {
        if (item == null || item.getId() == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.required"
            );
        }

        BigDecimal normalizedQuantity =
                defaultZero(quantity).setScale(0, RoundingMode.HALF_UP);

        if (normalizedQuantity.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Quantity must be greater than zero.",
                    ENTITY_NAME,
                    "quantity.invalid"
            );
        }

        BigDecimal normalizedUnitPrice =
                defaultZero(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (normalizedUnitPrice.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Unit price must be greater than zero.",
                    ENTITY_NAME,
                    "unitPrice.invalid"
            );
        }

        BigDecimal netAmount =
                normalizedQuantity
                        .multiply(normalizedUnitPrice)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (netAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Debit-note amount must be greater than zero.",
                    ENTITY_NAME,
                    "amount.invalid"
            );
        }

        billingChargeLineRepository
                .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                        item.getId(),
                        EXCLUDED_LINE_STATUSES
                )
                .ifPresent(existing ->
                        {
                            throw new BadRequestAlertException(
                                    "A charge line already exists for this service.",
                                    ENTITY_NAME,
                                    "chargeLine.duplicate"
                            );
                        }
                );

        BillingCharge charge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                                item.getEncounterId(),
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter charge not found for debit note adjustment.",
                                        ENTITY_NAME,
                                        "charge.notfound"
                                )
                        );

        Patient patient = findPatient(item.getPatientId());
        PatientEncounter encounter = findEncounter(item.getEncounterId());
        validatePatientMatchesEncounter(patient, encounter);

        String lineIdempotencyKey =
                idempotencyKeyPrefix + ":CHARGE_LINE";

        BillingChargeLine line =
                BillingChargeLine.builder()
                        .charge(charge)
                        .patientServiceProduct(item)
                        .patient(patient)
                        .encounter(encounter)
                        .billingItemType(item.getBillingItemType())
                        .brandMedicationId(item.getBrandMedicationId())
                        .diagnosticTestId(item.getDiagnosticTestId())
                        .serviceId(item.getServiceId())
                        .procedureId(item.getProcedureId())
                        .itemCode(resolveDebitNoteItemCode(item))
                        .itemDescription(resolveDebitNoteItemDescription(item))
                        .serviceSource(item.getServiceSource())
                        .sourceId(resolveSourceId(item))
                        .quantity(normalizedQuantity)
                        .unitPrice(normalizedUnitPrice)
                        .grossAmount(netAmount)
                        .discountAmount(BigDecimal.ZERO)
                        .exemptionAmount(BigDecimal.ZERO)
                        .taxAmount(BigDecimal.ZERO)
                        .netAmount(netAmount)
                        .patientResponsibilityAmount(
                                defaultZero(patientShareAmount)
                        )
                        .insuranceResponsibilityAmount(
                                defaultZero(insuranceShareAmount)
                        )
                        .otherPayerResponsibilityAmount(BigDecimal.ZERO)
                        .allocatedAmount(BigDecimal.ZERO)
                        .outstandingAmount(netAmount)
                        .reservedAmount(BigDecimal.ZERO)
                        .currency(item.getCurrency())
                        .status(BillingChargeLineStatus.OPEN)

                        .preAuthorizationRequired(
                                Boolean.TRUE.equals(
                                        item.getPreAuthorizationRequired()
                                )
                        )
                        .preAuthorizationStatus(
                                item.getPreAuthorizationStatus()
                        )
                        .preAuthorizationRequestId(
                                item.getPreAuthorizationRequestId()
                        )
                        .preAuthorizationReferenceNo(
                                item.getPreAuthorizationReferenceNo()
                        )

                        .idempotencyKey(lineIdempotencyKey)
                        .build();

        BillingChargeLine savedLine =
                billingChargeLineRepository.saveAndFlush(line);

        createPendingDebitNoteResponsibility(
                savedLine,
                item,
                ResponsiblePartyType.PATIENT,
                defaultZero(patientShareAmount),
                idempotencyKeyPrefix + ":RESPONSIBILITY:PATIENT"
        );

        createPendingDebitNoteResponsibility(
                savedLine,
                item,
                ResponsiblePartyType.INSURANCE,
                defaultZero(insuranceShareAmount),
                idempotencyKeyPrefix + ":RESPONSIBILITY:INSURANCE"
        );

        item.setUnitPrice(normalizedUnitPrice);
        item.setGrossAmount(netAmount);
        item.setNetAmount(netAmount);
        item.setTotalAmount(netAmount);
        item.setPatientShareAmount(defaultZero(patientShareAmount));
        item.setInsuranceShareAmount(defaultZero(insuranceShareAmount));
        item.setPaidAmount(BigDecimal.ZERO);
        item.setRemainingAmount(defaultZero(patientShareAmount));
        item.setPaymentStatus(PaymentStatus.PENDING);
        patientServiceAndProductRepository.save(item);

        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .idempotencyKey(idempotencyKeyPrefix)
                        .patientServiceProduct(item)
                        .charge(charge)
                        .chargeLine(savedLine)
                        .build();

        recalculateChargeTotals(context);

        BillingCharge updatedCharge = context.getCharge();
        if (updatedCharge.getStatus() == BillingChargeStatus.CLOSED
                && defaultZero(updatedCharge.getOutstandingAmount()).signum() > 0) {
            updatedCharge.setStatus(BillingChargeStatus.OPEN);
            billingChargeRepository.save(updatedCharge);
        }

        LOG.info(
                "[DEBIT_NOTE] Adjustment charge line created lineId={} chargeId={} pspId={} net={}",
                savedLine.getId(),
                charge.getId(),
                item.getId(),
                netAmount
        );

        return savedLine;
    }

    /**
     * Keeps encounter charge lines aligned when a credit note reduces an
     * invoice or debit-note financial document line.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void applyCreditNoteChargeLineSync(
            Long chargeLineId,
            BigDecimal creditAmount,
            BigDecimal financialItemNetAmount
    ) {
        if (chargeLineId == null
                || creditAmount == null
                || creditAmount.signum() <= 0) {
            return;
        }

        BillingChargeLine chargeLine =
                billingChargeLineRepository
                        .findById(chargeLineId)
                        .orElse(null);

        if (chargeLine == null
                || EXCLUDED_LINE_STATUSES.contains(
                        chargeLine.getStatus()
                )) {
            return;
        }

        BigDecimal patientResponsibility =
                defaultZero(
                        chargeLine.getPatientResponsibilityAmount()
                );

        if (patientResponsibility.signum() <= 0) {
            return;
        }

        BigDecimal chargeCredit =
                mapFinancialCreditToChargeAmount(
                        creditAmount,
                        financialItemNetAmount,
                        patientResponsibility
                );

        if (chargeCredit.signum() <= 0) {
            return;
        }

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByChargeLine_IdOrderByIdAsc(
                                chargeLineId
                        )
                        .stream()
                        .filter(responsibility ->
                                responsibility.getResponsiblePartyType()
                                        == ResponsiblePartyType.PATIENT
                        )
                        .filter(responsibility ->
                                responsibility.getStatus()
                                        != BillingResponsibilityStatus.CANCELLED
                                        && responsibility.getStatus()
                                        != BillingResponsibilityStatus.SUPERSEDED
                        )
                        .toList();

        BigDecimal remainingCredit = chargeCredit;

        for (BillingChargeResponsibility responsibility
                : responsibilities) {

            if (remainingCredit.signum() <= 0) {
                break;
            }

            BigDecimal responsibilityAmount =
                    defaultZero(
                            responsibility.getResponsibilityAmount()
                    );

            if (responsibilityAmount.signum() <= 0) {
                continue;
            }

            BigDecimal lineCredit =
                    remainingCredit.min(responsibilityAmount);

            BigDecimal newResponsibility =
                    responsibilityAmount.subtract(lineCredit);

            BigDecimal allocated =
                    defaultZero(
                            responsibility.getAllocatedAmount()
                    );

            BigDecimal newAllocated =
                    allocated.min(newResponsibility);

            responsibility.setResponsibilityAmount(
                    newResponsibility
            );
            responsibility.setAllocatedAmount(
                    newAllocated
            );
            responsibility.setOutstandingAmount(
                    newResponsibility.subtract(newAllocated)
            );

            if (newResponsibility.signum() == 0) {
                responsibility.setStatus(
                        BillingResponsibilityStatus.FULLY_ALLOCATED
                );
            } else if (newAllocated.signum() > 0) {
                responsibility.setStatus(
                        BillingResponsibilityStatus.PARTIALLY_ALLOCATED
                );
            } else {
                responsibility.setStatus(
                        BillingResponsibilityStatus.CALCULATED
                );
            }

            billingChargeResponsibilityRepository.save(
                    responsibility
            );

            remainingCredit =
                    remainingCredit.subtract(lineCredit);
        }

        BigDecimal appliedCredit =
                chargeCredit.subtract(remainingCredit);

        BigDecimal netAmount =
                defaultZero(chargeLine.getNetAmount());
        BigDecimal grossAmount =
                defaultZero(chargeLine.getGrossAmount());

        BigDecimal netReduction =
                scaleCreditToChargeAmount(
                        appliedCredit,
                        patientResponsibility,
                        netAmount
                );
        BigDecimal grossReduction =
                scaleCreditToChargeAmount(
                        appliedCredit,
                        patientResponsibility,
                        grossAmount
                );

        chargeLine.setNetAmount(
                netAmount.subtract(netReduction).max(BigDecimal.ZERO)
        );
        chargeLine.setGrossAmount(
                grossAmount.subtract(grossReduction).max(BigDecimal.ZERO)
        );
        syncChargeLineUnitPriceFromGross(chargeLine);
        chargeLine.setPatientResponsibilityAmount(
                patientResponsibility.subtract(appliedCredit)
                        .max(BigDecimal.ZERO)
        );

        BigDecimal currentAllocated =
                defaultZero(chargeLine.getAllocatedAmount());
        BigDecimal currentReserved =
                defaultZero(chargeLine.getReservedAmount());

        if (currentAllocated.compareTo(chargeLine.getNetAmount()) > 0) {
            chargeLine.setAllocatedAmount(chargeLine.getNetAmount());
        }

        chargeLine.setOutstandingAmount(
                defaultZero(chargeLine.getNetAmount())
                        .subtract(defaultZero(chargeLine.getAllocatedAmount()))
                        .subtract(currentReserved)
                        .max(BigDecimal.ZERO)
        );

        if (chargeLine.getNetAmount().signum() == 0) {
            chargeLine.setPatientResponsibilityAmount(BigDecimal.ZERO);
            chargeLine.setOutstandingAmount(BigDecimal.ZERO);
            chargeLine.setAllocatedAmount(BigDecimal.ZERO);
            chargeLine.setReservedAmount(BigDecimal.ZERO);
            chargeLine.setStatus(BillingChargeLineStatus.CANCELLED);
            chargeLine.setCancelledDate(Instant.now());
            chargeLine.setCancelledBy(
                    SecurityUtils.getCurrentUserLogin()
                            .orElse("system")
            );
            chargeLine.setCancellationReason(
                    "Credited via invoice adjustment."
            );
        } else if (chargeLine.getOutstandingAmount().signum() == 0
                && currentReserved.signum() == 0) {
            chargeLine.setStatus(
                    defaultZero(chargeLine.getAllocatedAmount()).signum() > 0
                            ? BillingChargeLineStatus.ALLOCATED
                            : BillingChargeLineStatus.OPEN
            );
        }

        billingChargeLineRepository.save(chargeLine);

        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .charge(chargeLine.getCharge())
                        .idempotencyKey(
                                "CREDIT_NOTE:CHARGE_SYNC:"
                                        + chargeLineId
                        )
                        .build();

        recalculateChargeTotals(context);

        LOG.info(
                "[CREDIT_NOTE] Charge line synced lineId={} credit={} "
                        + "net={} patientResponsibility={} outstanding={}",
                chargeLineId,
                appliedCredit,
                chargeLine.getNetAmount(),
                chargeLine.getPatientResponsibilityAmount(),
                chargeLine.getOutstandingAmount()
        );
    }

    /**
     * Keeps {@code gross_amount = ROUND(quantity * unit_price, 4)} and
     * {@code net_amount = gross - discount - exemption + tax} after credits
     * change the line total.
     */
    private void syncChargeLineUnitPriceFromGross(
            BillingChargeLine chargeLine
    ) {
        BigDecimal quantity =
                defaultZero(chargeLine.getQuantity());
        BigDecimal gross =
                defaultZero(chargeLine.getGrossAmount());

        if (quantity.signum() <= 0) {
            return;
        }

        if (gross.signum() == 0) {
            chargeLine.setUnitPrice(BigDecimal.ZERO);
            chargeLine.setGrossAmount(BigDecimal.ZERO);
            chargeLine.setNetAmount(BigDecimal.ZERO);
            return;
        }

        BigDecimal unitPrice =
                gross.divide(quantity, MONEY_SCALE, RoundingMode.HALF_UP);
        chargeLine.setUnitPrice(unitPrice);
        chargeLine.setGrossAmount(
                quantity.multiply(unitPrice)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        );

        BigDecimal discount =
                defaultZero(chargeLine.getDiscountAmount());
        BigDecimal exemption =
                defaultZero(chargeLine.getExemptionAmount());
        BigDecimal tax =
                defaultZero(chargeLine.getTaxAmount());

        chargeLine.setNetAmount(
                defaultZero(chargeLine.getGrossAmount())
                        .subtract(discount)
                        .subtract(exemption)
                        .add(tax)
                        .max(BigDecimal.ZERO)
        );
    }

    private BigDecimal scaleCreditToChargeAmount(
            BigDecimal creditAmount,
            BigDecimal chargeBasisAmount,
            BigDecimal chargeFieldAmount
    ) {
        if (creditAmount.signum() <= 0
                || chargeFieldAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (chargeBasisAmount.signum() <= 0) {
            return creditAmount.min(chargeFieldAmount);
        }

        return creditAmount
                .multiply(chargeFieldAmount)
                .divide(
                        chargeBasisAmount,
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                )
                .min(chargeFieldAmount);
    }

    private BigDecimal mapFinancialCreditToChargeAmount(
            BigDecimal creditAmount,
            BigDecimal financialItemNetAmount,
            BigDecimal patientResponsibility
    ) {
        if (financialItemNetAmount == null
                || financialItemNetAmount.signum() <= 0) {
            return creditAmount.min(patientResponsibility);
        }

        if (creditAmount.compareTo(financialItemNetAmount) >= 0) {
            return patientResponsibility;
        }

        return creditAmount
                .multiply(patientResponsibility)
                .divide(
                        financialItemNetAmount,
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                )
                .min(patientResponsibility);
    }

    private void createPendingDebitNoteResponsibility(
            BillingChargeLine chargeLine,
            PatientServiceAndProduct item,
            ResponsiblePartyType partyType,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (amount.signum() <= 0) {
            return;
        }

        BillingChargeResponsibility existing =
                billingChargeResponsibilityRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existing != null) {
            return;
        }

        BillingChargeResponsibility responsibility =
                BillingChargeResponsibility.builder()
                        .charge(chargeLine.getCharge())
                        .chargeLine(chargeLine)
                        .patientServiceProduct(item)
                        .patient(chargeLine.getPatient())
                        .encounter(chargeLine.getEncounter())
                        .responsiblePartyType(partyType)
                        .responsibilityRole(ResponsibilityRole.PRIMARY)
                        .responsibilityAmount(amount)
                        .allocatedAmount(BigDecimal.ZERO)
                        .outstandingAmount(amount)
                        .coveragePercentage(BigDecimal.ZERO)
                        .deductibleAmount(BigDecimal.ZERO)
                        .copayAmount(BigDecimal.ZERO)
                        .coinsuranceAmount(BigDecimal.ZERO)
                        .nonCoveredAmount(BigDecimal.ZERO)
                        .contractualAdjustmentAmount(BigDecimal.ZERO)
                        .currency(chargeLine.getCurrency())
                        .status(BillingResponsibilityStatus.CALCULATED)
                        .preAuthorizationRequired(
                                Boolean.TRUE.equals(
                                        item.getPreAuthorizationRequired()
                                )
                        )
                        .preAuthorizationStatus(
                                item.getPreAuthorizationStatus()
                        )
                        .preAuthorizationReferenceNo(
                                item.getPreAuthorizationReferenceNo()
                        )
                        .effectiveDate(Instant.now())
                        .idempotencyKey(idempotencyKey)
                        .build();

        billingChargeResponsibilityRepository.save(responsibility);
    }

    private String resolveDebitNoteItemCode(
            PatientServiceAndProduct item
    ) {
        if (item.getServiceId() != null) {
            return "SERVICE-" + item.getServiceId();
        }
        if (item.getProcedureId() != null) {
            return "PROCEDURE-" + item.getProcedureId();
        }
        if (item.getDiagnosticTestId() != null) {
            return "LAB-" + item.getDiagnosticTestId();
        }
        if (item.getBrandMedicationId() != null) {
            return "MED-" + item.getBrandMedicationId();
        }
        return item.getBillingItemType() == null
                ? "ITEM-" + item.getId()
                : item.getBillingItemType().name();
    }

    private String resolveDebitNoteItemDescription(
            PatientServiceAndProduct item
    ) {
        if (item.getNotes() != null && !item.getNotes().isBlank()) {
            return item.getNotes().trim();
        }

        return resolveDebitNoteItemCode(item);
    }

    /**
     * Cancels one charge line.
     *
     * Reservation and allocation must be released/reversed first.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void cancelChargeLine(
            BillingProcessingContext context,
            String reason,
            String cancelledBy
    ) {
        BillingChargeLine contextLine =
                requireChargeLine(context);

        validateCancellationData(
                reason,
                cancelledBy
        );

        BillingChargeLine line =
                billingChargeLineRepository
                        .findById(contextLine.getId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Billing charge line not found with id "
                                                + contextLine.getId(),
                                        ENTITY_NAME,
                                        "chargeLine.notfound"
                                )
                        );

        if (line.getStatus()
                == BillingChargeLineStatus.CANCELLED) {
            return;
        }

        if (defaultZero(
                line.getAllocatedAmount()
        ).signum() > 0) {
            throw new BadRequestAlertException(
                    "Charge line has active allocations. Reverse allocations before cancellation.",
                    ENTITY_NAME,
                    "chargeLine.hasAllocation"
            );
        }

        if (defaultZero(
                line.getReservedAmount()
        ).signum() > 0) {
            throw new BadRequestAlertException(
                    "Charge line has an active reservation. Release reservation before cancellation.",
                    ENTITY_NAME,
                    "chargeLine.hasReservation"
            );
        }

        line.setStatus(
                BillingChargeLineStatus.CANCELLED
        );
        line.setCancelledDate(Instant.now());
        line.setCancelledBy(cancelledBy);
        line.setCancellationReason(reason);

        billingChargeLineRepository.save(line);

        PatientServiceAndProduct item =
                line.getPatientServiceProduct();

        item.setPaymentStatus(
                PaymentStatus.CANCELLED
        );
        item.setRemainingAmount(
                BigDecimal.ZERO
        );

        patientServiceAndProductRepository.save(item);

        context.setCharge(line.getCharge());
        context.setChargeLine(line);

        recalculateChargeTotals(context);

        LOG.info(
                "[CANCEL_LINE] BillingChargeLine cancelled lineId={} pspId={} reason={} cancelledBy={}",
                line.getId(),
                item.getId(),
                reason,
                cancelledBy
        );
    }

    /**
     * Cancels all active charge lines for an encounter.
     *
     * Caller must release reservations and reverse allocations first.
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void cancelEncounterCharges(
            Long encounterId,
            String reason,
            String cancelledBy
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounter.required"
            );
        }

        validateCancellationData(
                reason,
                cancelledBy
        );

        List<BillingCharge> charges =
                billingChargeRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        BillingCharge::getId
                                )
                        )
                        .toList();

        Instant now = Instant.now();

        for (BillingCharge charge : charges) {

            List<BillingChargeLine> lines =
                    billingChargeLineRepository
                            .findAllByCharge_IdAndStatusNotInOrderByIdAsc(
                                    charge.getId(),
                                    EXCLUDED_LINE_STATUSES
                            );

            validateLinesCanBeCancelled(
                    charge,
                    lines
            );

            for (BillingChargeLine line : lines) {
                line.setStatus(
                        BillingChargeLineStatus.CANCELLED
                );
                line.setCancelledDate(now);
                line.setCancelledBy(cancelledBy);
                line.setCancellationReason(reason);

                PatientServiceAndProduct item =
                        line.getPatientServiceProduct();

                item.setPaymentStatus(
                        PaymentStatus.CANCELLED
                );
                item.setRemainingAmount(
                        BigDecimal.ZERO
                );
            }

            billingChargeLineRepository.saveAll(lines);

            patientServiceAndProductRepository.saveAll(
                    lines.stream()
                            .map(
                                    BillingChargeLine::
                                            getPatientServiceProduct
                            )
                            .toList()
            );

            charge.setStatus(
                    BillingChargeStatus.CANCELLED
            );
            charge.setCancelledDate(now);
            charge.setCancelledBy(cancelledBy);
            charge.setCancellationReason(reason);

            billingChargeRepository.save(charge);

            LOG.info(
                    "[CANCEL_ENCOUNTER] BillingCharge cancelled chargeId={} encounterId={} lineCount={}",
                    charge.getId(),
                    encounterId,
                    lines.size()
            );
        }
    }

    private void updateOperationalItemAfterPricing(
            PatientServiceAndProduct item,
            PriceCalculationResult pricing
    ) {
        item.setUnitPrice(pricing.unitPrice());
        item.setGrossAmount(pricing.grossAmount());
        item.setDiscountAmount(
                pricing.discountAmount()
        );
        item.setExemptionAmount(
                pricing.exemptionAmount()
        );
        item.setTaxAmount(pricing.taxAmount());
        item.setNetAmount(pricing.netAmount());
        item.setTotalAmount(pricing.netAmount());

        if (Boolean.TRUE.equals(
                item.getIsExempted()
        )) {
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
        } else {
            item.setRemainingAmount(
                    pricing.netAmount()
            );

            if (item.getPaymentStatus()
                    == PaymentStatus.EXEMPTED) {
                item.setPaymentStatus(
                        PaymentStatus.PENDING
                );
            }
        }

        patientServiceAndProductRepository.save(item);
    }

    private void validatePatientItem(
            PatientServiceAndProduct item
    ) {
        if (item.getPatientId() == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        if (item.getEncounterId() == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounter.required"
            );
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }

        if (item.getBillingItemType() == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity() <= 0) {
            throw new BadRequestAlertException(
                    "Quantity must be greater than zero.",
                    ENTITY_NAME,
                    "quantity.invalid"
            );
        }
    }

    private void validatePatientMatchesEncounter(
            Patient patient,
            PatientEncounter encounter
    ) {
        if (encounter.getPatient() == null
                || encounter.getPatient().getId() == null
                || !encounter.getPatient()
                .getId()
                .equals(patient.getId())) {

            throw new BadRequestAlertException(
                    "The patient does not belong to the encounter.",
                    ENTITY_NAME,
                    "patient.encounter.mismatch"
            );
        }
    }

    private Patient findPatient(
            Long patientId
    ) {
        return patientRepository
                .findById(patientId)
                .orElseThrow(() -> {
                    LOG.warn(
                            "[VALIDATE] Patient not found patientId={}",
                            patientId
                    );

                    return new NotFoundAlertException(
                            "Patient not found with id "
                                    + patientId,
                            ENTITY_NAME,
                            "patient.notfound"
                    );
                });
    }

    private PatientEncounter findEncounter(
            Long encounterId
    ) {
        return patientEncounterRepository
                .findById(encounterId)
                .orElseThrow(() -> {
                    LOG.warn(
                            "[VALIDATE] Encounter not found encounterId={}",
                            encounterId
                    );

                    return new NotFoundAlertException(
                            "Encounter not found with id "
                                    + encounterId,
                            ENTITY_NAME,
                            "encounter.notfound"
                    );
                });
    }

    private PatientInsurance loadPatientInsurance(
            PatientServiceAndProduct item
    ) {
        if (item.getPatientInsuranceId() == null) {
            return null;
        }

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

    private void validateCancellationData(
            String reason,
            String cancelledBy
    ) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required.",
                    ENTITY_NAME,
                    "cancellationReason.required"
            );
        }

        if (cancelledBy == null
                || cancelledBy.isBlank()) {
            throw new BadRequestAlertException(
                    "Cancelled-by user is required.",
                    ENTITY_NAME,
                    "cancelledBy.required"
            );
        }
    }

    private void validateLinesCanBeCancelled(
            BillingCharge charge,
            List<BillingChargeLine> lines
    ) {
        boolean hasAllocatedLine =
                lines.stream()
                        .anyMatch(line ->
                                defaultZero(
                                        line.getAllocatedAmount()
                                ).signum() > 0
                        );

        if (hasAllocatedLine) {
            throw new BadRequestAlertException(
                    "Charge " + charge.getId()
                            + " contains active allocations. "
                            + "Reverse allocations before cancellation.",
                    ENTITY_NAME,
                    "charge.hasAllocation"
            );
        }

        boolean hasReservedLine =
                lines.stream()
                        .anyMatch(line ->
                                defaultZero(
                                        line.getReservedAmount()
                                ).signum() > 0
                        );

        if (hasReservedLine) {
            throw new BadRequestAlertException(
                    "Charge " + charge.getId()
                            + " contains active reservations. "
                            + "Release reservations before cancellation.",
                    ENTITY_NAME,
                    "charge.hasReservation"
            );
        }
    }

    private BillingChargeLineStatus determineLineStatus(
            BillingChargeLine line,
            BillingChargeStatus chargeStatus
    ) {
        if (line.getStatus()
                == BillingChargeLineStatus.CANCELLED
                || line.getStatus()
                == BillingChargeLineStatus.REVERSED) {
            return line.getStatus();
        }

        BigDecimal netAmount =
                defaultZero(line.getNetAmount());
        BigDecimal allocatedAmount =
                defaultZero(line.getAllocatedAmount());
        BigDecimal outstandingAmount =
                defaultZero(line.getOutstandingAmount());
        BigDecimal reservedAmount =
                defaultZero(line.getReservedAmount());

        if (chargeStatus
                == BillingChargeStatus.CLOSED
                && outstandingAmount.signum() == 0) {
            return BillingChargeLineStatus.CLOSED;
        }

        if (outstandingAmount.signum() == 0
                && allocatedAmount.compareTo(
                netAmount
        ) >= 0
                && netAmount.signum() > 0) {
            return BillingChargeLineStatus.ALLOCATED;
        }

        if (allocatedAmount.signum() > 0
                && outstandingAmount.signum() > 0) {
            return BillingChargeLineStatus
                    .PARTIALLY_ALLOCATED;
        }

        if (reservedAmount.signum() > 0
                && allocatedAmount.signum() == 0) {
            return BillingChargeLineStatus.RESERVED;
        }

        if (line.getStatus()
                == BillingChargeLineStatus.DRAFT) {
            return BillingChargeLineStatus.DRAFT;
        }

        return BillingChargeLineStatus.OPEN;
    }

    private BillingChargeStatus determineChargeStatus(
            BillingChargeStatus currentStatus,
            int lineCount,
            BigDecimal netAmount,
            BigDecimal allocatedAmount,
            BigDecimal outstandingAmount
    ) {
        if (currentStatus
                == BillingChargeStatus.CANCELLED
                || currentStatus
                == BillingChargeStatus.REVERSED
                || currentStatus
                == BillingChargeStatus.CLOSED) {
            return currentStatus;
        }

        if (lineCount == 0
                || netAmount.signum() == 0) {
            return BillingChargeStatus.DRAFT;
        }

        if (allocatedAmount.signum() == 0) {
            return BillingChargeStatus.OPEN;
        }

        if (outstandingAmount.signum() == 0) {
            return BillingChargeStatus.FULLY_ALLOCATED;
        }

        return BillingChargeStatus.PARTIALLY_ALLOCATED;
    }

    private String resolveItemCode(
            BillingProcessingContext context
    ) {
        if (context.getPricingInput() == null) {
            return null;
        }

        return context.getPricingInput()
                .itemCode();
    }

    private String resolveItemDescription(
            BillingProcessingContext context
    ) {
        if (context.getPricingInput() != null
                && context.getPricingInput()
                .itemName() != null
                && !context.getPricingInput()
                .itemName()
                .isBlank()) {

            return context.getPricingInput()
                    .itemName();
        }

        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        return item.getBillingItemType()
                + " - "
                + resolveSourceId(item);
    }

    private Long resolveSourceId(
            PatientServiceAndProduct item
    ) {
        if (item.getServiceId() != null) {
            return item.getServiceId();
        }

        if (item.getProcedureId() != null) {
            return item.getProcedureId();
        }

        if (item.getDiagnosticTestId() != null) {
            return item.getDiagnosticTestId();
        }

        if (item.getBrandMedicationId() != null) {
            return item.getBrandMedicationId();
        }

        if (item.getSourceId() != null) {
            return item.getSourceId();
        }

        throw new BadRequestAlertException(
                "Patient service/product has no source ID.",
                ENTITY_NAME,
                "sourceId.required"
        );
    }

    private String generateChargeNumber() {
        return "CHG-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private PatientServiceAndProduct
    requirePatientServiceProduct(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPatientServiceProduct()
                == null) {

            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.required"
            );
        }

        return context.getPatientServiceProduct();
    }

    private BillingCharge requireCharge(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getCharge() == null) {

            throw new BadRequestAlertException(
                    "Billing charge is required.",
                    ENTITY_NAME,
                    "charge.required"
            );
        }

        return context.getCharge();
    }

    private BillingChargeLine requireChargeLine(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getChargeLine() == null) {

            throw new BadRequestAlertException(
                    "Billing charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
            );
        }

        return context.getChargeLine();
    }

    private PriceCalculationResult
    requirePricingResult(
            BillingProcessingContext context
    ) {
        if (context == null
                || context.getPricingResult() == null) {

            throw new BadRequestAlertException(
                    "Pricing result is required before charge-line processing.",
                    ENTITY_NAME,
                    "pricingResult.required"
            );
        }

        return context.getPricingResult();
    }

    private BigDecimal defaultZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private <T> BigDecimal sum(
            List<T> values,
            Function<T, BigDecimal> getter
    ) {
        return values.stream()
                .map(getter)
                .map(this::defaultZero)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    private RuntimeException handleConstraintViolation(
            Exception exception
    ) {
        Throwable rootCause =
                getRootCause(exception);

        String rootMessage =
                rootCause != null
                        ? rootCause.getMessage()
                        : exception.getMessage();

        String message =
                rootMessage == null
                        ? ""
                        : rootMessage.toLowerCase();

        LOG.warn(
                "[DB_CONSTRAINT] BillingCharge constraint violation rootMessage={}",
                rootMessage,
                exception
        );

        if (message.contains(
                "uq_billing_charge_number"
        )) {
            return new BadRequestAlertException(
                    "Generated charge number already exists.",
                    ENTITY_NAME,
                    "chargeNumber.duplicate"
            );
        }

        if (message.contains(
                "uq_billing_charge_idempotency"
        )) {
            return new BadRequestAlertException(
                    "The billing charge request was already processed.",
                    ENTITY_NAME,
                    "idempotency.duplicate"
            );
        }

        if (message.contains(
                "uq_billing_charge_line_psp_active"
        )) {
            return new BadRequestAlertException(
                    "An active billing charge line already exists for this patient service/product.",
                    ENTITY_NAME,
                    "chargeLine.active.duplicate"
            );
        }

        if (message.contains(
                "uq_billing_charge_line_idempotency"
        )) {
            return new BadRequestAlertException(
                    "The billing charge-line request was already processed.",
                    ENTITY_NAME,
                    "chargeLine.idempotency.duplicate"
            );
        }

        if (message.contains(
                "fk_billing_charge_patient"
        )) {
            return new NotFoundAlertException(
                    "Patient not found.",
                    ENTITY_NAME,
                    "patient.notfound"
            );
        }

        if (message.contains(
                "fk_billing_charge_encounter"
        )) {
            return new NotFoundAlertException(
                    "Encounter not found.",
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while processing billing charge.",
                ENTITY_NAME,
                "db.constraint"
        );
    }
}
