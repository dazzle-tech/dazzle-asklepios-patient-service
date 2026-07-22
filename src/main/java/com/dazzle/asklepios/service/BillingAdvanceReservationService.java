package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;

public interface BillingAdvanceReservationService {

    void reserveAvailableAdvance(BillingProcessingContext context);

    void adjustReservation(BillingProcessingContext context);

    void releaseReservation(BillingProcessingContext context);

    void releaseEncounterReservations(
            Long encounterId,
            String idempotencyKey
    );
}