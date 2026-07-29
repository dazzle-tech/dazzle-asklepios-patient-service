package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvoiceChargePaymentSyncService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InvoiceChargePaymentSyncService.class);

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.SUPERSEDED
            );

    private final BillingChargeLineRepository billingChargeLineRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingChargeService billingChargeService;

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
            Long chargeLineId = item.getBillingChargeLineId();
            if (chargeLineId == null) {
                continue;
            }

            BillingChargeLine line =
                    billingChargeLineRepository
                            .findById(chargeLineId)
                            .orElse(null);

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

        BigDecimal chargeCollected = money(chargeLine.getAllocatedAmount());
        if (chargeCollected.signum() <= 0) {
            return;
        }

        BigDecimal patientShare = money(item.getPatientShareAmount());
        BigDecimal currentPaid = money(item.getPaidAmount());
        BigDecimal syncedPaid = chargeCollected.min(patientShare);

        if (syncedPaid.compareTo(currentPaid) <= 0) {
            return;
        }

        BigDecimal remaining =
                patientShare
                        .subtract(syncedPaid)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        item.setPaidAmount(syncedPaid);
        item.setRemainingAmount(remaining);
        item.setStatus(resolveStatus(syncedPaid, patientShare, remaining));
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
        BigDecimal currentAllocated =
                money(chargeLine.getAllocatedAmount());

        BigDecimal targetAllocated =
                mapDocumentPaymentToChargeAmount(
                        item,
                        documentPaid,
                        documentShare,
                        lineNet
                );

        if (targetAllocated.compareTo(currentAllocated) <= 0) {
            return false;
        }

        chargeLine.setAllocatedAmount(targetAllocated);
        chargeLine.setOutstandingAmount(
                lineNet
                        .subtract(targetAllocated)
                        .max(BigDecimal.ZERO)
        );

        billingChargeLineRepository.save(chargeLine);

        syncPatientResponsibility(
                chargeLine,
                item,
                documentPaid,
                documentShare
        );

        return true;
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
