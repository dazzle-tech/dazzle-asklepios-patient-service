package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;

public interface BillingLedgerService {

    void recordChargeCreation(BillingProcessingContext context);

    void recordChargeCancellation(BillingProcessingContext context);
}