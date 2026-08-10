package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.event.EligibilityCheckSucceededEvent;
import com.dazzle.asklepios.service.BillingResponsibilityService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EncounterInsuranceResponsibilityRefreshService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    EncounterInsuranceResponsibilityRefreshService.class
            );

    private final BillingResponsibilityService billingResponsibilityService;
    private final EncounterInsuranceResponsibilityRefreshService self;

    public EncounterInsuranceResponsibilityRefreshService(
            BillingResponsibilityService billingResponsibilityService,
            @Lazy EncounterInsuranceResponsibilityRefreshService self
    ) {
        this.billingResponsibilityService = billingResponsibilityService;
        this.self = self;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEligibilityCheckSucceeded(EligibilityCheckSucceededEvent event) {
        if (event == null || event.encounterId() == null) {
            return;
        }

        LOG.info(
                "[ELIGIBILITY_REFRESH] Refreshing encounter billing responsibilities "
                        + "encounterId={}",
                event.encounterId()
        );

        self.refreshEncounter(event.encounterId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refreshEncounter(Long encounterId) {
        return billingResponsibilityService
                .refreshInsuranceResponsibilitiesForEncounter(encounterId);
    }
}
