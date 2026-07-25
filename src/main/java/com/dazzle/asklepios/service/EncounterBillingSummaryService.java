package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.repository.BillingWalletRepository;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.service.dto.billing.BillingResponsibilitySummary;
import com.dazzle.asklepios.service.dto.billing.BillingWalletSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingItemSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EncounterBillingSummaryService {

    private static final String ENTITY_NAME =
            "encounterBillingSummary";

    private static final int MONEY_SCALE =
            4;

    private static final EnumSet<BillingChargeStatus>
            EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(
                    BillingChargeStatus.CANCELLED,
                    BillingChargeStatus.REVERSED
            );

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.SUPERSEDED
            );

    private final PatientEncounterRepository
            patientEncounterRepository;

    private final BillingChargeRepository
            billingChargeRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingWalletRepository
            billingWalletRepository;

    private final BillingPricingSnapshotRepository
            billingPricingSnapshotRepository;

    private final BillingAllocationRepository
            billingAllocationRepository;

    /**
     * Returns an empty summary when the encounter does not yet have a
     * financial charge. This prevents the billing screen from receiving
     * 404 before "Prepare Services" is executed.
     */
    public EncounterBillingSummary getByEncounterId(
            Long encounterId
    ) {
        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient encounter not found with id "
                                                + encounterId,
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        BillingCharge charge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                                encounterId,
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .orElse(null);

        if (charge == null) {
            return emptySummary(
                    encounter
            );
        }

        List<BillingChargeLine> lines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EnumSet.of(
                                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus.CANCELLED,
                                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus.REVERSED
                                )
                        )
                        .stream()
                        .filter(line ->
                                charge.getId().equals(
                                        line.getCharge().getId()
                                )
                        )
                        .toList();

        List<BillingChargeResponsibility> encounterResponsibilities =
                billingChargeResponsibilityRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        List<BillingChargeResponsibility> chargeResponsibilities =
                encounterResponsibilities
                        .stream()
                        .filter(responsibility ->
                                charge.getId().equals(
                                        responsibility
                                                .getCharge()
                                                .getId()
                                )
                        )
                        .toList();

        Map<Long, List<BillingChargeResponsibility>>
                responsibilitiesByChargeLineId =
                encounterResponsibilities
                        .stream()
                        .collect(Collectors.groupingBy(
                                responsibility ->
                                        responsibility
                                                .getChargeLine()
                                                .getId()
                        ));

        ResponsibilityTotals patientTotals =
                totalsFor(
                        chargeResponsibilities,
                        ResponsiblePartyType.PATIENT
                );

        ResponsibilityTotals insuranceTotals =
                totalsFor(
                        chargeResponsibilities,
                        ResponsiblePartyType.INSURANCE
                );

        ResponsibilityTotals otherPayerTotals =
                totalsFor(
                        chargeResponsibilities,
                        ResponsiblePartyType.OTHER_PAYER
                );

        BillingWalletSummary wallet =
                buildWalletSummary(
                        charge.getPatient().getId(),
                        charge.getCurrency()
                );

        List<EncounterBillingItemSummary> items =
                lines.stream()
                        .map(line ->
                                buildItemSummary(
                                        line,
                                        responsibilitiesByChargeLineId
                                                .getOrDefault(
                                                        line.getId(),
                                                        List.of()
                                                )
                                )
                        )
                        .toList();

        SettlementTotals settlementTotals =
                settlementTotalsFor(
                        encounter.getId(),
                        charge.getId()
                );

        return new EncounterBillingSummary(
                charge.getId(),
                charge.getChargeNumber(),
                charge.getPatient().getId(),
                charge.getEncounter().getId(),
                charge.getChargeDate(),
                charge.getCurrency(),
                money(charge.getGrossAmount()),
                money(charge.getDiscountAmount()),
                money(charge.getExemptionAmount()),
                money(charge.getTaxAmount()),
                money(charge.getNetAmount()),
                money(charge.getAllocatedAmount()),
                money(charge.getOutstandingAmount()),
                charge.getLineCount(),
                charge.getStatus(),
                patientTotals.responsibilityAmount(),
                patientTotals.allocatedAmount(),
                patientTotals.outstandingAmount(),
                settlementTotals.walletSettledAmount(),
                settlementTotals.debitSettledAmount(),
                insuranceTotals.responsibilityAmount(),
                insuranceTotals.allocatedAmount(),
                insuranceTotals.outstandingAmount(),
                otherPayerTotals.responsibilityAmount(),
                otherPayerTotals.allocatedAmount(),
                otherPayerTotals.outstandingAmount(),
                wallet,
                items
        );
    }

    private EncounterBillingItemSummary buildItemSummary(
            BillingChargeLine line,
            List<BillingChargeResponsibility> responsibilities
    ) {
        PatientServiceAndProduct item =
                line.getPatientServiceProduct();

        PricingDisplayFields pricingDisplay =
                resolvePricingDisplayFields(
                        line.getId()
                );

        return new EncounterBillingItemSummary(
                item == null
                        ? null
                        : item.getId(),
                line.getId(),
                line.getBillingItemType() == null
                        ? null
                        : line.getBillingItemType().name(),
                line.getSourceId(),
                line.getItemCode(),
                line.getItemDescription(),
                money(line.getQuantity()),
                money(line.getUnitPrice()),
                pricingDisplay.setupUnitPrice(),
                pricingDisplay.priceSource(),
                pricingDisplay.priceListItemCode(),
                money(line.getGrossAmount()),
                money(line.getDiscountAmount()),
                money(line.getExemptionAmount()),
                money(line.getTaxAmount()),
                money(line.getNetAmount()),
                money(line.getPatientResponsibilityAmount()),
                money(line.getInsuranceResponsibilityAmount()),
                money(line.getOtherPayerResponsibilityAmount()),
                money(line.getReservedAmount()),
                money(line.getAllocatedAmount()),
                money(line.getOutstandingAmount()),
                item != null
                        && Boolean.TRUE.equals(
                        item.getIsExempted()
                ),
                line.getCurrency(),
                line.getStatus(),
                responsibilities.stream()
                        .map(this::buildResponsibilitySummary)
                        .toList()
        );
    }

    private BillingResponsibilitySummary buildResponsibilitySummary(
            BillingChargeResponsibility responsibility
    ) {
        return new BillingResponsibilitySummary(
                responsibility.getId(),
                responsibility.getResponsiblePartyType(),
                responsibility.getResponsibilityRole(),
                responsibility.getPayerId(),
                responsibility.getPatientInsurance() == null
                        ? null
                        : responsibility
                        .getPatientInsurance()
                        .getId(),
                money(
                        responsibility
                                .getResponsibilityAmount()
                ),
                money(
                        responsibility
                                .getAllocatedAmount()
                ),
                money(
                        responsibility
                                .getOutstandingAmount()
                ),
                rate(
                        responsibility
                                .getCoveragePercentage()
                ),
                money(
                        responsibility
                                .getDeductibleAmount()
                ),
                money(
                        responsibility
                                .getCopayAmount()
                ),
                money(
                        responsibility
                                .getCoinsuranceAmount()
                ),
                money(
                        responsibility
                                .getNonCoveredAmount()
                ),
                responsibility.getCurrency(),
                responsibility.getStatus()
        );
    }

    private BillingWalletSummary buildWalletSummary(
            Long patientId,
            Currency currency
    ) {
        BillingWallet wallet =
                billingWalletRepository
                        .findByPatient_IdAndCurrency(
                                patientId,
                                currency
                        )
                        .orElse(null);

        if (wallet == null) {
            return emptyWallet(
                    currency
            );
        }

        return new BillingWalletSummary(
                wallet.getId(),
                money(wallet.getCreditedAmount()),
                money(wallet.getAvailableBalance()),
                money(wallet.getReservedBalance()),
                money(wallet.getConsumedAmount()),
                money(wallet.getRefundedAmount()),
                wallet.getCurrency(),
                wallet.getStatus()
        );
    }

    private EncounterBillingSummary emptySummary(
            PatientEncounter encounter
    ) {
        Long patientId =
                encounter.getPatient() == null
                        ? null
                        : encounter.getPatient().getId();

        return new EncounterBillingSummary(
                null,
                null,
                patientId,
                encounter.getId(),
                null,
                null,
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                0,
                null,
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                emptyWallet(null),
                List.of()
        );
    }

    private SettlementTotals settlementTotalsFor(
            Long encounterId,
            Long chargeId
    ) {
        List<BillingAllocation> allocations =
                billingAllocationRepository
                        .findAllByEncounter_IdAndCharge_IdAndStatusInOrderByAllocationDateAscIdAsc(
                                encounterId,
                                chargeId,
                                EnumSet.of(
                                        BillingAllocationStatus.ACTIVE,
                                        BillingAllocationStatus.PARTIALLY_REVERSED
                                )
                        );

        BigDecimal walletSettled =
                zero();
        BigDecimal debitSettled =
                zero();

        for (BillingAllocation allocation
                : allocations) {

            BigDecimal amount =
                    money(
                            allocation
                                    .getRemainingAllocatedAmount()
                    );

            if (amount.signum() <= 0) {
                continue;
            }

            AllocationSourceType sourceType =
                    allocation.getAllocationSourceType();

            if (sourceType == null) {
                continue;
            }

            switch (sourceType) {
                case RESERVATION,
                     WALLET_AVAILABLE,
                     PAYMENT ->
                        walletSettled =
                                walletSettled.add(
                                        amount
                                );
                case DEBIT ->
                        debitSettled =
                                debitSettled.add(
                                        amount
                                );
                default -> {
                    // Insurance and other payers are tracked separately.
                }
            }
        }

        return new SettlementTotals(
                money(walletSettled),
                money(debitSettled)
        );
    }

    private record SettlementTotals(
            BigDecimal walletSettledAmount,
            BigDecimal debitSettledAmount
    ) {
    }

    private BillingWalletSummary emptyWallet(
            Currency currency
    ) {
        return new BillingWalletSummary(
                null,
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                currency,
                null
        );
    }

    private ResponsibilityTotals totalsFor(
            List<BillingChargeResponsibility> responsibilities,
            ResponsiblePartyType partyType
    ) {
        BigDecimal responsibilityAmount =
                zero();

        BigDecimal allocatedAmount =
                zero();

        BigDecimal outstandingAmount =
                zero();

        for (BillingChargeResponsibility responsibility
                : responsibilities) {

            if (responsibility.getResponsiblePartyType()
                    != partyType) {
                continue;
            }

            responsibilityAmount =
                    responsibilityAmount.add(
                            money(
                                    responsibility
                                            .getResponsibilityAmount()
                            )
                    );

            allocatedAmount =
                    allocatedAmount.add(
                            money(
                                    responsibility
                                            .getAllocatedAmount()
                            )
                    );

            outstandingAmount =
                    outstandingAmount.add(
                            money(
                                    responsibility
                                            .getOutstandingAmount()
                            )
                    );
        }

        return new ResponsibilityTotals(
                money(responsibilityAmount),
                money(allocatedAmount),
                money(outstandingAmount)
        );
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return (value == null
                ? BigDecimal.ZERO
                : value)
                .setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal rate(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private PricingDisplayFields resolvePricingDisplayFields(
            Long chargeLineId
    ) {
        return billingPricingSnapshotRepository
                .findTopByChargeLine_IdAndStatusOrderByIdDesc(
                        chargeLineId,
                        BillingPricingSnapshotStatus.ACTIVE
                )
                .map(snapshot -> {
                    JsonNode payload =
                            snapshot.getCalculationPayload();

                    BigDecimal setupUnitPrice =
                            readPayloadDecimal(
                                    payload,
                                    "setupUnitPrice"
                            );

                    if (setupUnitPrice == null) {
                        setupUnitPrice =
                                money(
                                        snapshot.getBaseUnitPrice()
                                );
                    }

                    String priceSource =
                            payload != null
                                    && payload.hasNonNull(
                                    "priceSource"
                            )
                                    ? payload.get(
                                            "priceSource"
                                    ).asText()
                                    : snapshot.getPriceSource() == null
                                    ? null
                                    : snapshot.getPriceSource().name();

                    String priceListItemCode =
                            payload != null
                                    && payload.hasNonNull(
                                    "priceListItemCode"
                            )
                                    ? payload.get(
                                            "priceListItemCode"
                                    ).asText()
                                    : snapshot.getPriceListItemCode();

                    return new PricingDisplayFields(
                            setupUnitPrice,
                            priceSource,
                            priceListItemCode
                    );
                })
                .orElseGet(
                        () ->
                                new PricingDisplayFields(
                                        null,
                                        null,
                                        null
                                )
                );
    }

    private BigDecimal readPayloadDecimal(
            JsonNode payload,
            String fieldName
    ) {
        if (
                payload == null
                        || !payload.hasNonNull(
                        fieldName
                )
        ) {
            return null;
        }

        return money(
                payload.get(fieldName)
                        .decimalValue()
        );
    }

    private record PricingDisplayFields(
            BigDecimal setupUnitPrice,
            String priceSource,
            String priceListItemCode
    ) {
    }

    private record ResponsibilityTotals(
            BigDecimal responsibilityAmount,
            BigDecimal allocatedAmount,
            BigDecimal outstandingAmount
    ) {
    }
}
