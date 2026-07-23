package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
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

    private final BillingChargeRepository billingChargeRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
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
                        .status(resolveInitialLineStatus(item, pricing))

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
        PatientServiceAndProduct item =
                requirePatientServiceProduct(context);

        BillingChargeLine line =
                billingChargeLineRepository
                        .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                                item.getId(),
                                EXCLUDED_LINE_STATUSES
                        )
                        .orElseThrow(() -> {
                            LOG.warn(
                                    "[LOAD_LINE] Active charge line not found pspId={}",
                                    item.getId()
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

    private BillingChargeLineStatus resolveInitialLineStatus(
            PatientServiceAndProduct item,
            PriceCalculationResult pricing
    ) {
        if (Boolean.TRUE.equals(
                item.getIsExempted()
        )) {
            /*
             * Keep DRAFT until responsibility processing finishes.
             * The line has zero financial outstanding amount.
             */
            return BillingChargeLineStatus.DRAFT;
        }

        if (pricing.netAmount().signum() == 0) {
            return BillingChargeLineStatus.DRAFT;
        }

        return BillingChargeLineStatus.OPEN;
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
        if (item.getSourceId() != null) {
            return item.getSourceId();
        }

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