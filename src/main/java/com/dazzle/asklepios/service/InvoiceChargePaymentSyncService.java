package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class InvoiceChargePaymentSyncService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InvoiceChargePaymentSyncService.class);

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.SUPERSEDED
            );

    private static final EnumSet<BillingAllocationStatus>
            ACTIVE_ALLOCATION_STATUSES =
            EnumSet.of(
                    BillingAllocationStatus.ACTIVE,
                    BillingAllocationStatus.PARTIALLY_REVERSED
            );

    private static final EnumSet<AllocationSourceType>
            CASH_EQUIVALENT_ALLOCATION_SOURCES =
            EnumSet.of(
                    AllocationSourceType.RESERVATION,
                    AllocationSourceType.WALLET_AVAILABLE,
                    AllocationSourceType.PAYMENT
            );

    private final BillingChargeLineRepository billingChargeLineRepository;

    private final BillingAllocationRepository billingAllocationRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingChargeService billingChargeService;

    private final FinancialDocumentRepository financialDocumentRepository;

    private final FinancialDocumentItemRepository financialDocumentItemRepository;

    /**
     * Keeps charge-line allocation state aligned with issued invoice payments.
     */
    public void reconcileInvoicePaymentsForEncounter(Long encounterId) {
        if (encounterId == null) {
            return;
        }

        financialDocumentRepository
                .findFirstByEncounterIdAndDocumentTypeOrderByIdDesc(
                        encounterId,
                        FinancialDocumentType.INVOICE
                )
                .ifPresent(invoice ->
                        syncPostInvoicePayments(
                                loadBalancePaymentItems(invoice.getId())
                        )
                );
    }

    private List<FinancialDocumentItem> loadBalancePaymentItems(Long invoiceId) {
        List<FinancialDocumentItem> invoiceItems =
                financialDocumentItemRepository.findByDocument_Id(invoiceId);

        List<FinancialDocumentItem> debitNoteItems =
                financialDocumentRepository
                        .findAllByParentDocumentId(invoiceId)
                        .stream()
                        .filter(document ->
                                document.getDocumentType()
                                        == FinancialDocumentType.DEBIT_NOTE
                        )
                        .flatMap(document ->
                                financialDocumentItemRepository
                                        .findByDocument_Id(document.getId())
                                        .stream()
                        )
                        .toList();

        return Stream.concat(
                        invoiceItems.stream(),
                        debitNoteItems.stream()
                )
                .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                .toList();
    }

    /**
     * Credits charge-line collections toward invoice line balances.
     */
    public void syncPreInvoicePayments(List<FinancialDocumentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (FinancialDocumentItem item : items) {
            syncItemFromChargeLine(item);
        }

        LOG.info(
                "[INVOICE_PAYMENT_SYNC] synced {} invoice line(s) from charge",
                items.size()
        );
    }

    /**
     * Pushes financial-document payments back to encounter charge lines so
     * billing workspace remaining matches invoice paid status.
     */
    public void syncPostInvoicePayments(List<FinancialDocumentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Set<Long> chargeIds = new HashSet<>();

        for (FinancialDocumentItem item : items) {
            BillingChargeLine line = resolveChargeLine(item);
            if (line == null) {
                continue;
            }

            if (syncItemToChargeLine(item, line)) {
                chargeIds.add(line.getCharge().getId());
            }
        }

        for (Long chargeId : chargeIds) {
            BillingCharge charge = new BillingCharge();
            charge.setId(chargeId);

            billingChargeService.recalculateChargeTotals(
                    BillingProcessingContext.builder()
                            .charge(charge)
                            .idempotencyKey(
                                    "INVOICE_PAYMENT_SYNC:CHARGE:"
                                            + chargeId
                            )
                            .build()
            );
        }

        LOG.info(
                "[INVOICE_PAYMENT_SYNC] pushed document payments for {} line(s), {} charge(s)",
                items.size(),
                chargeIds.size()
        );
    }

    private void syncItemFromChargeLine(FinancialDocumentItem item) {
        Long chargeLineId = item.getBillingChargeLineId();
        if (chargeLineId == null) {
            return;
        }

        BillingChargeLine chargeLine =
                billingChargeLineRepository
                        .findById(chargeLineId)
                        .orElse(null);

        if (chargeLine == null) {
            return;
        }

        BigDecimal patientShare = money(item.getPatientShareAmount());
        BigDecimal collectibleShare =
                patientShare.signum() > 0
                        ? patientShare
                        : money(item.getNetAmount());
        BigDecimal cashCollected =
                sumCashEquivalentCollections(chargeLineId);
        BigDecimal syncedFromCharge = cashCollected.min(collectibleShare);
        BigDecimal existingPaid = money(item.getPaidAmount());
        // Keep direct invoice payments (e.g. invoice-level tax) when re-syncing from charge.
        BigDecimal mergedPaid = existingPaid.max(syncedFromCharge);

        BigDecimal remaining =
                collectibleShare
                        .subtract(mergedPaid)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        item.setPaidAmount(mergedPaid);
        item.setRemainingAmount(remaining);
        item.setStatus(
                resolveStatus(mergedPaid, collectibleShare, remaining)
        );
    }

    /**
     * Wallet reservations and direct payments count as invoice collections.
     * Debit allocations only close the charge and must still be collected
     * from the invoice / patient debit account.
     */
    private BigDecimal sumCashEquivalentCollections(
            Long chargeLineId
    ) {
        if (chargeLineId == null) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal total = BigDecimal.ZERO;

        List<BillingAllocation> allocations =
                billingAllocationRepository
                        .findAllByChargeLine_IdAndStatusInOrderByAllocationDateDescIdDesc(
                                chargeLineId,
                                ACTIVE_ALLOCATION_STATUSES
                        );

        for (BillingAllocation allocation : allocations) {
            AllocationSourceType sourceType =
                    allocation.getAllocationSourceType();

            if (sourceType == null
                    || !CASH_EQUIVALENT_ALLOCATION_SOURCES.contains(
                            sourceType
                    )) {
                continue;
            }

            BigDecimal amount =
                    money(
                            allocation.getRemainingAllocatedAmount()
                    );

            if (amount.signum() > 0) {
                total = total.add(amount);
            }
        }

        return money(total);
    }

    private boolean syncItemToChargeLine(
            FinancialDocumentItem item,
            BillingChargeLine chargeLine
    ) {
        if (item.getBillingChargeLineId() == null) {
            return false;
        }

        BigDecimal documentPaid = money(item.getPaidAmount());
        if (documentPaid.signum() <= 0) {
            return false;
        }

        BigDecimal documentShare = money(item.getPatientShareAmount());
        BigDecimal lineNet = money(chargeLine.getNetAmount());
        BigDecimal patientExposure =
                money(chargeLine.getPatientResponsibilityAmount());
        if (patientExposure.signum() <= 0) {
            patientExposure = lineNet;
        }
        BigDecimal currentAllocated =
                money(chargeLine.getAllocatedAmount());

        BigDecimal targetAllocated =
                mapDocumentPaymentToChargeAmount(
                        item,
                        documentPaid,
                        documentShare,
                        patientExposure
                );

        if (targetAllocated.compareTo(currentAllocated) == 0) {
            releaseExcessReservation(chargeLine, patientExposure, targetAllocated);
            billingChargeLineRepository.save(chargeLine);
            return false;
        }

        chargeLine.setAllocatedAmount(targetAllocated);
        chargeLine.setOutstandingAmount(
                patientExposure
                        .subtract(targetAllocated)
                        .max(BigDecimal.ZERO)
        );
        releaseExcessReservation(chargeLine, patientExposure, targetAllocated);

        billingChargeLineRepository.save(chargeLine);

        syncPatientResponsibility(
                chargeLine,
                item,
                documentPaid,
                documentShare
        );

        return true;
    }

    private BillingChargeLine resolveChargeLine(FinancialDocumentItem item) {
        if (item == null) {
            return null;
        }

        Long chargeLineId = item.getBillingChargeLineId();
        if (chargeLineId != null) {
            return billingChargeLineRepository.findById(chargeLineId).orElse(null);
        }

        if (item.getPatientServiceProductId() == null) {
            return null;
        }

        return billingChargeLineRepository
                .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                        item.getPatientServiceProductId(),
                        EXCLUDED_LINE_STATUSES
                )
                .orElse(null);
    }

    private void syncPatientResponsibility(
            BillingChargeLine chargeLine,
            FinancialDocumentItem item,
            BigDecimal documentPaid,
            BigDecimal documentShare
    ) {
        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByChargeLine_IdAndStatusNotInOrderByIdAsc(
                                chargeLine.getId(),
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        for (BillingChargeResponsibility responsibility
                : responsibilities) {

            if (responsibility.getResponsiblePartyType()
                    != ResponsiblePartyType.PATIENT) {
                continue;
            }

            BigDecimal responsibilityAmount =
                    money(responsibility.getResponsibilityAmount());

            if (responsibilityAmount.signum() <= 0) {
                continue;
            }

            BigDecimal targetAllocated =
                    mapDocumentPaymentToChargeAmount(
                            item,
                            documentPaid,
                            documentShare,
                            responsibilityAmount
                    );

            BigDecimal currentAllocated =
                    money(responsibility.getAllocatedAmount());

            if (targetAllocated.compareTo(currentAllocated) <= 0) {
                continue;
            }

            responsibility.setAllocatedAmount(targetAllocated);
            responsibility.setOutstandingAmount(
                    responsibilityAmount
                            .subtract(targetAllocated)
                            .max(BigDecimal.ZERO)
            );
            responsibility.setStatus(
                    resolveResponsibilityStatus(
                            targetAllocated,
                            responsibilityAmount
                    )
            );

            billingChargeResponsibilityRepository.save(
                    responsibility
            );
        }
    }

    /**
     * Reserved + allocated must not exceed patient exposure
     * (ck_billing_charge_line_reserved_limit).
     */
    private void releaseExcessReservation(
            BillingChargeLine chargeLine,
            BigDecimal patientExposure,
            BigDecimal targetAllocated
    ) {
        BigDecimal reserved = money(chargeLine.getReservedAmount());
        if (reserved.signum() <= 0) {
            return;
        }

        BigDecimal maxReserved =
                patientExposure
                        .subtract(targetAllocated)
                        .max(BigDecimal.ZERO);

        if (reserved.compareTo(maxReserved) > 0) {
            chargeLine.setReservedAmount(maxReserved);
        }
    }

    private BigDecimal mapDocumentPaymentToChargeAmount(
            FinancialDocumentItem item,
            BigDecimal documentPaid,
            BigDecimal documentShare,
            BigDecimal chargeFieldAmount
    ) {
        if (chargeFieldAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        if (money(item.getRemainingAmount()).signum() == 0
                && documentShare.signum() > 0) {
            return chargeFieldAmount;
        }

        if (documentShare.signum() <= 0) {
            return documentPaid.min(chargeFieldAmount);
        }

        return documentPaid
                .multiply(chargeFieldAmount)
                .divide(
                        documentShare,
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                )
                .min(chargeFieldAmount);
    }

    private BillingResponsibilityStatus resolveResponsibilityStatus(
            BigDecimal allocatedAmount,
            BigDecimal responsibilityAmount
    ) {
        if (allocatedAmount.compareTo(responsibilityAmount) >= 0) {
            return BillingResponsibilityStatus.FULLY_ALLOCATED;
        }

        if (allocatedAmount.signum() > 0) {
            return BillingResponsibilityStatus.PARTIALLY_ALLOCATED;
        }

        return BillingResponsibilityStatus.CALCULATED;
    }

    private FinancialDocumentItemStatus resolveStatus(
            BigDecimal paidAmount,
            BigDecimal patientShare,
            BigDecimal remaining
    ) {
        if (remaining.signum() <= 0 && patientShare.signum() > 0) {
            return FinancialDocumentItemStatus.PAID;
        }

        if (paidAmount.signum() > 0) {
            return FinancialDocumentItemStatus.PARTIALLY_PAID;
        }

        return FinancialDocumentItemStatus.PENDING;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
