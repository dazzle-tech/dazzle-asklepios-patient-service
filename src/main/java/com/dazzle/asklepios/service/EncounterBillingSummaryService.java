package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.repository.BillingWalletRepository;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingResponsibilitySummary;
import com.dazzle.asklepios.service.dto.billing.BillingWalletSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingItemSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterInvoiceBalance;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EncounterBillingSummaryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    EncounterBillingSummaryService.class
            );

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

    private final BillingItemDisplayNameService
            billingItemDisplayNameService;

    private final EncounterInvoiceBalanceService
            encounterInvoiceBalanceService;

    private final FinancialDocumentItemRepository
            financialDocumentItemRepository;

    private final PatientServiceAndProductRepository
            patientServiceAndProductRepository;

    private final BillingEngineService
            billingEngineService;

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
                        .or(() ->
                                billingChargeRepository
                                        .findFirstByEncounter_IdOrderByIdDesc(
                                                encounterId
                                        )
                        )
                        .orElse(null);

        if (charge == null) {
            EncounterInvoiceBalance invoiceBalance =
                    encounterInvoiceBalanceService.resolveForEncounter(
                            encounterId
                    );

            return emptySummary(
                    encounter,
                    buildUnbilledItemSummaries(
                            encounter,
                            List.of()
                    ),
                    invoiceBalance
            );
        }

        List<BillingChargeLine> lines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EnumSet.of(
                                        BillingChargeLineStatus.REVERSED
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
                new ArrayList<>(
                        buildItemSummaries(
                                lines,
                                responsibilitiesByChargeLineId
                        )
                );
        items.addAll(
                buildUnbilledItemSummaries(
                        encounter,
                        items
                )
        );

        SettlementTotals settlementTotals =
                settlementTotalsFor(
                        encounter.getId(),
                        charge.getId()
                );

        EncounterInvoiceBalance invoiceBalance =
                encounterInvoiceBalanceService.resolveForEncounter(
                        encounterId
                );

        if (invoiceBalance.invoiceId() != null) {
            items = enrichItemsFromInvoice(
                    items,
                    invoiceBalance.invoiceId()
            );
        }

        BigDecimal walletSettledAmount =
                settlementTotals.walletSettledAmount();

        if (invoiceBalance.invoiceId() != null) {
            walletSettledAmount =
                    walletSettledAmount.max(
                            money(invoiceBalance.paidAmount())
                    );
        }

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
                walletSettledAmount,
                settlementTotals.debitSettledAmount(),
                insuranceTotals.responsibilityAmount(),
                insuranceTotals.allocatedAmount(),
                insuranceTotals.outstandingAmount(),
                otherPayerTotals.responsibilityAmount(),
                otherPayerTotals.allocatedAmount(),
                otherPayerTotals.outstandingAmount(),
                wallet,
                invoiceBalance.invoiceId(),
                invoiceBalance.invoiceNumber(),
                invoiceBalance.totalAmount(),
                invoiceBalance.paidAmount(),
                invoiceBalance.outstandingAmount(),
                items,
                encounter.getCoverageType(),
                encounter.getPatientInsuranceId()
        );
    }

    private List<EncounterBillingItemSummary> buildItemSummaries(
            List<BillingChargeLine> lines,
            Map<Long, List<BillingChargeResponsibility>> responsibilitiesByChargeLineId
    ) {
        Map<String, String> displayNameCache = new HashMap<>();

        return lines.stream()
                .map(line ->
                        buildItemSummary(
                                line,
                                responsibilitiesByChargeLineId
                                        .getOrDefault(
                                                line.getId(),
                                                List.of()
                                        ),
                                displayNameCache
                        )
                )
                .toList();
    }

    private EncounterBillingItemSummary buildItemSummary(
            BillingChargeLine line,
            List<BillingChargeResponsibility> responsibilities,
            Map<String, String> displayNameCache
    ) {
        PatientServiceAndProduct item =
                line.getPatientServiceProduct();

        PricingDisplayFields pricingDisplay =
                resolvePricingDisplayFields(
                        line.getId()
                );

        String priceSource =
                resolveEffectivePriceSource(
                        pricingDisplay,
                        item
                );

        String itemName =
                billingItemDisplayNameService.resolveDisplayName(
                        line.getBillingItemType(),
                        item,
                        line.getSourceId(),
                        line.getItemDescription(),
                        displayNameCache
                );

        BigDecimal unitPrice =
                resolveDisplayUnitPrice(
                        line,
                        item,
                        pricingDisplay
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
                itemName,
                money(line.getQuantity()),
                unitPrice,
                pricingDisplay.setupUnitPrice(),
                priceSource,
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
                line.getCreatedDate(),
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

    private List<EncounterBillingItemSummary> buildUnbilledItemSummaries(
            PatientEncounter encounter,
            List<EncounterBillingItemSummary> billedItems
    ) {
        Set<Long> billedPspIds =
                billedItems
                        .stream()
                        .map(
                                EncounterBillingItemSummary::patientServiceProductId
                        )
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<String, String> displayNameCache =
                new HashMap<>();

        return patientServiceAndProductRepository
                .findByEncounterId(
                        encounter.getId()
                )
                .stream()
                .filter(item ->
                        !billedPspIds.contains(
                                item.getId()
                        )
                )
                .filter(this::isEligibleUnbilledItem)
                .map(item ->
                        buildUnbilledItemSummary(
                                encounter,
                                item,
                                displayNameCache
                        )
                )
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isEligibleUnbilledItem(PatientServiceAndProduct item) {
        if (item == null || item.getId() == null) {
            return false;
        }

        PaymentStatus paymentStatus = item.getPaymentStatus();
        if (paymentStatus == PaymentStatus.CANCELLED
                || paymentStatus == PaymentStatus.EXEMPTED
                || paymentStatus == PaymentStatus.SKIPPED_PENDING_PRE_AUTH) {
            return false;
        }

        return true;
    }

    private EncounterBillingItemSummary buildUnbilledItemSummary(
            PatientEncounter encounter,
            PatientServiceAndProduct item,
            Map<String, String> displayNameCache
    ) {
        ResolvedBillingPrice resolvedPrice;

        try {
            resolvedPrice =
                    billingEngineService.resolvePricing(
                            item,
                            encounter.getFacilityId()
                    );
        } catch (RuntimeException exception) {
            LOG.debug(
                    "[BILLING_SUMMARY] Unable to resolve pricing "
                            + "for unbilled pspId={} reason={}",
                    item.getId(),
                    exception.getMessage()
            );
            return null;
        }

        BigDecimal quantity =
                money(
                        BigDecimal.valueOf(
                                item.getQuantity()
                        )
                );
        BigDecimal unitPrice =
                money(
                        resolvedPrice.unitPrice()
                );
        BigDecimal grossAmount =
                money(
                        unitPrice.multiply(
                                quantity
                        )
                );
        BigDecimal netAmount =
                money(
                        grossAmount
                                .subtract(
                                        money(
                                                item.getDiscountAmount()
                                        )
                                )
                                .subtract(
                                        money(
                                                item.getExemptionAmount()
                                        )
                                )
                                .add(
                                        money(
                                                item.getTaxAmount()
                                        )
                                )
                );

        BillingPricingResolveResponse pricingResponse =
                resolvedPrice.pricingResponse();
        String priceListItemCode =
                pricingResponse == null
                        ? null
                        : pricingResponse.priceListItemCode();
        String priceSource =
                resolvedPrice.priceSource() == null
                        ? null
                        : resolvedPrice.priceSource().name();

        String itemName =
                billingItemDisplayNameService.resolveDisplayName(
                        item.getBillingItemType(),
                        item,
                        item.getSourceId(),
                        null,
                        displayNameCache
                );

        BigDecimal storedPatientShare = money(item.getPatientShareAmount());
        BigDecimal storedInsuranceShare = money(item.getInsuranceShareAmount());
        boolean hasStoredSplit =
                storedPatientShare.signum() > 0 || storedInsuranceShare.signum() > 0;
        BigDecimal patientShare = hasStoredSplit ? storedPatientShare : netAmount;
        BigDecimal insuranceShare = hasStoredSplit ? storedInsuranceShare : zero();

        return new EncounterBillingItemSummary(
                item.getId(),
                null,
                item.getBillingItemType() == null
                        ? null
                        : item.getBillingItemType().name(),
                item.getSourceId(),
                null,
                itemName,
                quantity,
                unitPrice,
                money(
                        resolvedPrice.setupUnitPrice()
                ),
                priceSource,
                priceListItemCode,
                grossAmount,
                money(item.getDiscountAmount()),
                money(item.getExemptionAmount()),
                money(item.getTaxAmount()),
                netAmount,
                patientShare,
                insuranceShare,
                zero(),
                zero(),
                zero(),
                patientShare,
                Boolean.TRUE.equals(
                        item.getIsExempted()
                ),
                item.getCurrency(),
                BillingChargeLineStatus.DRAFT,
                item.getCreatedDate(),
                List.of()
        );
    }

    private EncounterBillingSummary emptySummary(
            PatientEncounter encounter,
            List<EncounterBillingItemSummary> items,
            EncounterInvoiceBalance invoiceBalance
    ) {
        Long patientId =
                encounter.getPatient() == null
                        ? null
                        : encounter.getPatient().getId();

        EncounterInvoiceBalance resolvedInvoiceBalance =
                invoiceBalance == null
                        ? EncounterInvoiceBalance.empty()
                        : invoiceBalance;

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
                resolvedInvoiceBalance.invoiceId(),
                resolvedInvoiceBalance.invoiceNumber(),
                resolvedInvoiceBalance.totalAmount(),
                resolvedInvoiceBalance.paidAmount(),
                resolvedInvoiceBalance.outstandingAmount(),
                items == null
                        ? List.of()
                        : items,
                encounter.getCoverageType(),
                encounter.getPatientInsuranceId()
        );
    }

    private List<EncounterBillingItemSummary> enrichItemsFromInvoice(
            List<EncounterBillingItemSummary> items,
            Long invoiceId
    ) {
        Map<Long, FinancialDocumentItem> invoiceItemsByChargeLine =
                financialDocumentItemRepository
                        .findByDocument_Id(invoiceId)
                        .stream()
                        .filter(item -> item.getBillingChargeLineId() != null)
                        .collect(Collectors.toMap(
                                FinancialDocumentItem::getBillingChargeLineId,
                                item -> item,
                                (left, right) -> left
                        ));

        if (invoiceItemsByChargeLine.isEmpty()) {
            return items;
        }

        return items.stream()
                .map(item -> overlayInvoiceCollections(
                        item,
                        invoiceItemsByChargeLine.get(item.chargeLineId())
                ))
                .toList();
    }

    private EncounterBillingItemSummary overlayInvoiceCollections(
            EncounterBillingItemSummary item,
            FinancialDocumentItem invoiceItem
    ) {
        if (invoiceItem == null || item.chargeLineId() == null) {
            return item;
        }

        BigDecimal patientShare =
                money(item.patientResponsibilityAmount());

        if (patientShare.signum() <= 0) {
            patientShare = money(item.netAmount());
        }

        BigDecimal paidOnInvoice =
                money(invoiceItem.getPaidAmount());

        if (paidOnInvoice.signum() <= 0) {
            return item;
        }

        BigDecimal allocated =
                paidOnInvoice.min(patientShare);

        BigDecimal storedRemaining =
                money(invoiceItem.getRemainingAmount());

        BigDecimal outstanding =
                storedRemaining.signum() > 0
                        ? storedRemaining
                        : patientShare
                                .subtract(allocated)
                                .max(zero());

        if (allocated.compareTo(
                money(item.allocatedAmount())
        ) <= 0) {
            return item;
        }

        return new EncounterBillingItemSummary(
                item.patientServiceProductId(),
                item.chargeLineId(),
                item.billingItemType(),
                item.sourceId(),
                item.itemCode(),
                item.itemName(),
                item.quantity(),
                item.unitPrice(),
                item.setupUnitPrice(),
                item.priceSource(),
                item.priceListItemCode(),
                item.grossAmount(),
                item.discountAmount(),
                item.exemptionAmount(),
                item.taxAmount(),
                item.netAmount(),
                item.patientResponsibilityAmount(),
                item.insuranceResponsibilityAmount(),
                item.otherPayerResponsibilityAmount(),
                item.reservedAmount(),
                allocated,
                outstanding,
                item.exempted(),
                item.currency(),
                item.status(),
                item.chargedAt(),
                item.responsibilities()
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

                    BigDecimal resolvedUnitPrice =
                            readPayloadDecimal(
                                    payload,
                                    "unitPrice"
                            );

                    if (
                            resolvedUnitPrice == null
                                    || resolvedUnitPrice.signum() <= 0
                    ) {
                        resolvedUnitPrice =
                                money(
                                        snapshot.getBaseUnitPrice()
                                );
                    }

                    BigDecimal setupUnitPrice =
                            readPayloadDecimal(
                                    payload,
                                    "setupUnitPrice"
                            );

                    if (setupUnitPrice == null) {
                        setupUnitPrice =
                                resolvedUnitPrice;
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

                    if (
                            priceSource == null
                                    && payload != null
                                    && payload.hasNonNull(
                                    "pricingSource"
                            )
                    ) {
                        priceSource =
                                mapPricingSourceToPriceSource(
                                        payload.get(
                                                "pricingSource"
                                        ).asText()
                                );
                    }

                    String priceListItemCode =
                            payload != null
                                    && payload.hasNonNull(
                                    "priceListItemCode"
                            )
                                    ? payload.get(
                                            "priceListItemCode"
                                    ).asText()
                                    : snapshot.getPriceListItemCode();

                    if (
                            priceSource == null
                                    && (
                                    snapshot.getPriceListItemId()
                                            != null
                                            || (
                                            priceListItemCode
                                                    != null
                                                    && !priceListItemCode.isBlank()
                                    )
                            )
                    ) {
                        priceSource =
                                BillingPriceSource.PRICE_LIST.name();
                    }

                    return new PricingDisplayFields(
                            setupUnitPrice,
                            resolvedUnitPrice,
                            priceSource,
                            priceListItemCode
                    );
                })
                .orElseGet(
                        () ->
                                new PricingDisplayFields(
                                        null,
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

    private BigDecimal resolveDisplayUnitPrice(
            BillingChargeLine line,
            PatientServiceAndProduct item,
            PricingDisplayFields pricingDisplay
    ) {
        /*
         * Cancelled/reversed lines keep accounting zeros.
         * Never re-resolve catalog/setup pricing for them — that resurrects
         * "Setup Fallback" and unpaid remaining in the billing UI.
         */
        if (line.getStatus() == BillingChargeLineStatus.CANCELLED
                || line.getStatus() == BillingChargeLineStatus.REVERSED) {
            return money(line.getUnitPrice());
        }

        if (
                pricingDisplay.resolvedUnitPrice() != null
                        && pricingDisplay.resolvedUnitPrice().signum() > 0
        ) {
            return pricingDisplay.resolvedUnitPrice();
        }

        if (
                item != null
                        && line.getEncounter() != null
                        && line.getEncounter().getFacilityId() != null
        ) {
            try {
                ResolvedBillingPrice resolvedPrice =
                        billingEngineService.resolvePricing(
                                item,
                                line.getEncounter().getFacilityId()
                        );

                if (
                        resolvedPrice.unitPrice() != null
                                && resolvedPrice.unitPrice().signum() > 0
                ) {
                    return money(
                            resolvedPrice.unitPrice()
                    );
                }
            } catch (RuntimeException exception) {
                LOG.debug(
                        "[BILLING_SUMMARY] Unable to resolve display unit price "
                                + "for chargeLineId={} reason={}",
                        line.getId(),
                        exception.getMessage()
                );
            }
        }

        return money(
                line.getUnitPrice()
        );
    }

    private String resolveEffectivePriceSource(
            PricingDisplayFields pricingDisplay,
            PatientServiceAndProduct item
    ) {
        if (
                pricingDisplay.priceSource() != null
                        && !pricingDisplay.priceSource().isBlank()
        ) {
            return pricingDisplay.priceSource();
        }

        if (
                pricingDisplay.priceListItemCode() != null
                        && !pricingDisplay.priceListItemCode().isBlank()
        ) {
            return BillingPriceSource.PRICE_LIST.name();
        }

        if (item != null && item.getPriceSource() != null) {
            return mapPatientPriceSource(item.getPriceSource());
        }

        return null;
    }

    private String mapPatientPriceSource(
            PriceSource source
    ) {
        return switch (source) {
            case PRICE_LIST, INSURANCE_CONTRACT, WASEEL_SBS ->
                    BillingPriceSource.PRICE_LIST.name();
            default ->
                    BillingPriceSource.SETUP_FALLBACK.name();
        };
    }

    private String mapPricingSourceToPriceSource(
            String pricingSource
    ) {
        if (pricingSource == null || pricingSource.isBlank()) {
            return null;
        }

        try {
            return switch (PricingSource.valueOf(pricingSource)) {
                case PRICE_LIST,
                     INSURANCE_PRICE_LIST,
                     CASH_PRICE_LIST ->
                        BillingPriceSource.PRICE_LIST.name();
                default ->
                        BillingPriceSource.SETUP_FALLBACK.name();
            };
        } catch (IllegalArgumentException ex) {
            return BillingPriceSource.SETUP_FALLBACK.name();
        }
    }

    private record PricingDisplayFields(
            BigDecimal setupUnitPrice,
            BigDecimal resolvedUnitPrice,
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
