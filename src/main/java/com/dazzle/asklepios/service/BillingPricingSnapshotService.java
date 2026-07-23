package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ExemptionType;
import com.dazzle.asklepios.domain.enumeration.billing.PricingReason;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BillingPricingSnapshotService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingPricingSnapshotService.class
            );

    private static final String ENTITY_NAME =
            "billingPricingSnapshot";

    private final BillingPricingSnapshotRepository
            billingPricingSnapshotRepository;

    private final ObjectMapper objectMapper;

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingPricingSnapshot createInitialSnapshot(
            BillingProcessingContext context
    ) {
        validateContext(context);

        String idempotencyKey =
                context.getIdempotencyKey()
                        + ":PRICING_SNAPSHOT";

        BillingPricingSnapshot existing =
                billingPricingSnapshotRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existing != null) {
            context.setPricingSnapshot(existing);
            return existing;
        }

        BillingChargeLine chargeLine =
                context.getChargeLine();

        BillingPricingSnapshot active =
                billingPricingSnapshotRepository
                        .findFirstByChargeLine_IdAndStatusOrderByIdDesc(
                                chargeLine.getId(),
                                BillingPricingSnapshotStatus.ACTIVE
                        )
                        .orElse(null);

        if (active != null) {
            context.setPricingSnapshot(active);
            return active;
        }

        BillingPricingSnapshot snapshot =
                buildSnapshot(
                        context,
                        idempotencyKey,
                        PricingReason.INITIAL_PRICING
                );

        BillingPricingSnapshot saved =
                billingPricingSnapshotRepository
                        .saveAndFlush(snapshot);

        context.setPricingSnapshot(saved);

        chargeLine.setCurrentPricingSnapshotId(saved.getId());

        LOG.info(
                "[CREATE] Pricing snapshot created "
                        + "snapshotId={} chargeLineId={} "
                        + "gross={} discount={} exemption={} tax={} net={}",
                saved.getId(),
                chargeLine.getId(),
                saved.getGrossAmount(),
                saved.getDiscountAmount(),
                saved.getExemptionAmount(),
                saved.getTaxAmount(),
                saved.getNetAmount()
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingPricingSnapshot createRepricingSnapshot(
            BillingProcessingContext context,
            PricingReason pricingReason,
            String overrideReason
    ) {
        validateContext(context);

        if (pricingReason == null) {
            throw new BadRequestAlertException(
                    "Pricing reason is required.",
                    ENTITY_NAME,
                    "pricingReason.required"
            );
        }

        BillingChargeLine chargeLine =
                context.getChargeLine();

        String idempotencyKey =
                context.getIdempotencyKey()
                        + ":REPRICING_SNAPSHOT";

        BillingPricingSnapshot existing =
                billingPricingSnapshotRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existing != null) {
            context.setPricingSnapshot(existing);
            return existing;
        }

        BillingPricingSnapshot previous =
                billingPricingSnapshotRepository
                        .findFirstByChargeLine_IdAndStatusOrderByIdDesc(
                                chargeLine.getId(),
                                BillingPricingSnapshotStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Active pricing snapshot not found for charge line "
                                                + chargeLine.getId(),
                                        ENTITY_NAME,
                                        "activeSnapshot.notfound"
                                )
                        );

        previous.setStatus(
                BillingPricingSnapshotStatus.SUPERSEDED
        );
        previous.setSupersededDate(
                Instant.now()
        );

        billingPricingSnapshotRepository
                .saveAndFlush(previous);

        BillingPricingSnapshot newSnapshot =
                buildSnapshot(
                        context,
                        idempotencyKey,
                        pricingReason
                );

        newSnapshot.setOverrideReason(
                trimToNull(overrideReason)
        );

        BillingPricingSnapshot saved =
                billingPricingSnapshotRepository
                        .saveAndFlush(newSnapshot);

        previous.setSupersededBySnapshot(saved);

        billingPricingSnapshotRepository.save(previous);

        chargeLine.setCurrentPricingSnapshotId(saved.getId());

        context.setPricingSnapshot(saved);

        LOG.info(
                "[REPRICE] Pricing snapshot created "
                        + "previousSnapshotId={} newSnapshotId={} "
                        + "chargeLineId={} reason={}",
                previous.getId(),
                saved.getId(),
                chargeLine.getId(),
                pricingReason
        );

        return saved;
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void cancelActiveSnapshot(
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

        BillingPricingSnapshot snapshot =
                billingPricingSnapshotRepository
                        .findFirstByChargeLine_IdAndStatusOrderByIdDesc(
                                context.getChargeLine().getId(),
                                BillingPricingSnapshotStatus.ACTIVE
                        )
                        .orElse(null);

        if (snapshot == null) {
            return;
        }

        snapshot.setStatus(
                BillingPricingSnapshotStatus.CANCELLED
        );

        snapshot.setSupersededDate(
                Instant.now()
        );

        snapshot.setOverrideReason(
                trimToNull(reason)
        );

        billingPricingSnapshotRepository.save(snapshot);

        context.setPricingSnapshot(snapshot);

        LOG.info(
                "[CANCEL] Pricing snapshot cancelled "
                        + "snapshotId={} chargeLineId={}",
                snapshot.getId(),
                context.getChargeLine().getId()
        );
    }

    private BillingPricingSnapshot buildSnapshot(
            BillingProcessingContext context,
            String idempotencyKey,
            PricingReason pricingReason
    ) {
        BillingChargeLine chargeLine =
                context.getChargeLine();

        PatientServiceAndProduct item =
                context.getPatientServiceProduct();

        BillingPricingInput input =
                context.getPricingInput();

        PriceCalculationResult result =
                context.getPricingResult();

        return BillingPricingSnapshot.builder()
                .chargeLine(chargeLine)
                .patientServiceProduct(item)
                .patient(chargeLine.getPatient())
                .encounter(chargeLine.getEncounter())

                .priceListId(input.priceListId())
                .priceListItemId(
                        input.priceListItemId()
                )

                .billingConfigurationId(null)

                .discountId(input.discountId())
                .taxId(input.taxId())

                .pricingSource(
                        input.pricingSource()
                )

                .pricingVersion(
                        input.pricingVersion()
                )

                .priceListCode(
                        trimToNull(
                                input.priceListCode()
                        )
                )

                .priceListName(
                        trimToNull(
                                input.priceListName()
                        )
                )

                .priceListItemCode(
                        trimToNull(
                                input.priceListItemCode()
                        )
                )

                .quantity(result.quantity())
                .baseUnitPrice(result.unitPrice())
                .grossAmount(result.grossAmount())

                .discountType(
                        input.discountType()
                )

                .discountRate(
                        defaultZero(
                                input.discountRate()
                        )
                )

                .discountAmount(
                        result.discountAmount()
                )

                .discountReason(null)
                .discountApprovedBy(null)

                .exemptionType(
                        resolveExemptionType(
                                item,
                                result
                        )
                )

                .exemptionRate(
                        calculateExemptionRate(result)
                )

                .exemptionAmount(
                        result.exemptionAmount()
                )

                .exemptionReason(
                        Boolean.TRUE.equals(
                                item.getIsExempted()
                        )
                                ? "Service marked as exempted"
                                : null
                )

                .taxType(input.taxType())

                .taxRate(
                        defaultZero(input.taxRate())
                )

                .taxableAmount(
                        result.taxableAmount()
                )

                .taxAmount(result.taxAmount())

                .netAmount(result.netAmount())

                .currency(input.currency())

                .calculationOrder(
                        input.calculationOrder()
                )

                .roundingMode(
                        input.roundingMode()
                )

                .roundingScale(
                        input.roundingScale() == null
                                ? 4
                                : input.roundingScale()
                )

                .patientResponsibilityAmount(
                        money(
                                context
                                        .getPatientResponsibilityAmount()
                        )
                )

                .insuranceResponsibilityAmount(
                        money(
                                context
                                        .getInsuranceResponsibilityAmount()
                        )
                )

                .otherPayerResponsibilityAmount(
                        money(
                                context
                                        .getOtherPayerResponsibilityAmount()
                        )
                )

                .calculationPayload(
                        buildCalculationPayload(context)
                )

                .status(
                        BillingPricingSnapshotStatus.ACTIVE
                )

                .effectiveDate(
                        Instant.now()
                )

                .pricingReason(
                        pricingReason
                )

                .idempotencyKey(
                        idempotencyKey
                )

                .build();
    }

    private ObjectNode buildCalculationPayload(
            BillingProcessingContext context
    ) {
        BillingPricingInput input =
                context.getPricingInput();

        PriceCalculationResult result =
                context.getPricingResult();

        ObjectNode payload =
                objectMapper.createObjectNode();

        put(payload, "priceListId", input.priceListId());
        put(payload, "priceListItemId", input.priceListItemId());

        put(payload, "quantity", result.quantity());
        put(payload, "unitPrice", result.unitPrice());
        put(payload, "grossAmount", result.grossAmount());
        put(payload, "discountAmount", result.discountAmount());
        put(payload, "exemptionAmount", result.exemptionAmount());
        put(payload, "taxableAmount", result.taxableAmount());
        put(payload, "taxAmount", result.taxAmount());
        put(payload, "netAmount", result.netAmount());

        put(
                payload,
                "patientResponsibilityAmount",
                context.getPatientResponsibilityAmount()
        );

        put(
                payload,
                "insuranceResponsibilityAmount",
                context.getInsuranceResponsibilityAmount()
        );

        put(
                payload,
                "otherPayerResponsibilityAmount",
                context.getOtherPayerResponsibilityAmount()
        );

        if (input.pricingSource() != null) {
            payload.put(
                    "pricingSource",
                    input.pricingSource().name()
            );
        }

        if (input.discountType() != null) {
            payload.put(
                    "discountType",
                    input.discountType().name()
            );
        }

        if (input.taxType() != null) {
            payload.put(
                    "taxType",
                    input.taxType().name()
            );
        }

        if (input.calculationOrder() != null) {
            payload.put(
                    "calculationOrder",
                    input.calculationOrder().name()
            );
        }

        if (input.roundingMode() != null) {
            payload.put(
                    "roundingMode",
                    input.roundingMode().name()
            );
        }

        payload.put(
                "roundingScale",
                input.roundingScale() == null
                        ? 4
                        : input.roundingScale()
        );

        return payload;
    }

    private ExemptionType resolveExemptionType(
            PatientServiceAndProduct item,
            PriceCalculationResult result
    ) {
        if (Boolean.TRUE.equals(
                item.getIsExempted()
        )) {
            return ExemptionType.FULL;
        }

        if (defaultZero(
                result.exemptionAmount()
        ).signum() > 0) {
            return ExemptionType.PARTIAL;
        }

        return null;
    }

    private BigDecimal calculateExemptionRate(
            PriceCalculationResult result
    ) {
        BigDecimal gross =
                money(result.grossAmount());

        BigDecimal exemption =
                money(result.exemptionAmount());

        if (gross.signum() == 0
                || exemption.signum() == 0) {
            return BigDecimal.ZERO
                    .setScale(
                            6,
                            RoundingMode.HALF_UP
                    );
        }

        return exemption
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        gross,
                        6,
                        RoundingMode.HALF_UP
                );
    }

    private void validateContext(
            BillingProcessingContext context
    ) {
        if (context == null) {
            throw new BadRequestAlertException(
                    "Billing context is required.",
                    ENTITY_NAME,
                    "context.required"
            );
        }

        if (context.getChargeLine() == null
                || context.getChargeLine().getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted charge line is required.",
                    ENTITY_NAME,
                    "chargeLine.required"
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

        if (context.getPricingInput() == null) {
            throw new BadRequestAlertException(
                    "Pricing input is required.",
                    ENTITY_NAME,
                    "pricingInput.required"
            );
        }

        if (context.getPricingResult() == null) {
            throw new BadRequestAlertException(
                    "Pricing result is required.",
                    ENTITY_NAME,
                    "pricingResult.required"
            );
        }

        validateResponsibilityBalance(context);
    }

    private void validateResponsibilityBalance(
            BillingProcessingContext context
    ) {
        BigDecimal total =
                money(
                        context
                                .getPatientResponsibilityAmount()
                )
                        .add(
                                money(
                                        context
                                                .getInsuranceResponsibilityAmount()
                                )
                        )
                        .add(
                                money(
                                        context
                                                .getOtherPayerResponsibilityAmount()
                                )
                        );

        BigDecimal net =
                money(
                        context
                                .getPricingResult()
                                .netAmount()
                );

        if (total.compareTo(net) != 0) {
            throw new BadRequestAlertException(
                    "Responsibility total must equal net amount before creating pricing snapshot.",
                    ENTITY_NAME,
                    "responsibility.balance.invalid"
            );
        }
    }

    private void put(
            ObjectNode node,
            String field,
            Long value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void put(
            ObjectNode node,
            String field,
            BigDecimal value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return defaultZero(value)
                .setScale(
                        4,
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

    private String trimToNull(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}