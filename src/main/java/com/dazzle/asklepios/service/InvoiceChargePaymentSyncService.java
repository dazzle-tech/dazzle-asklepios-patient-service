package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceChargePaymentSyncService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InvoiceChargePaymentSyncService.class);

    private static final int MONEY_SCALE = 4;

    private final BillingChargeLineRepository billingChargeLineRepository;

    /**
     * Credits charge-line collections toward invoice line balances.
     */
    public void syncPreInvoicePayments(List<FinancialDocumentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (FinancialDocumentItem item : items) {
            syncItem(item);
        }

        LOG.info(
                "[INVOICE_PAYMENT_SYNC] synced {} invoice line(s)",
                items.size()
        );
    }

    private void syncItem(FinancialDocumentItem item) {
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
        BigDecimal appliedPayment = chargeCollected.min(patientShare);
        BigDecimal remaining =
                patientShare
                        .subtract(appliedPayment)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        item.setPaidAmount(appliedPayment);
        item.setRemainingAmount(remaining);
        item.setStatus(resolveStatus(appliedPayment, patientShare, remaining));
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
